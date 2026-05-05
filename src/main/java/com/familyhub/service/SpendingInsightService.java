package com.familyhub.service;

import com.familyhub.entity.enums.SpendingCategory;
import com.familyhub.repository.ReceiptItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Generates a short Gemini-powered spending insight for the dashboard widget.
 * Result is cached per family and evicted when a new receipt is parsed.
 *
 * Insight mode is chosen based on how far into the current month we are:
 *   Week 1 (days 1–7)  → previous month full summary (not enough current data)
 *   Week 2 (days 8–14) → Week 1 snapshot of current month
 *   Week 3+ (day 15+)  → last two completed weeks compared
 *   Past months        → always full monthly summary
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingInsightService {

    private final ReceiptItemRepository receiptItemRepository;
    private final GeminiClient geminiClient;

    @Cacheable(value = "spendingInsight", key = "#familyId")
    public String getInsight(Long familyId) {
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.now();
        int week = weekOfMonth(today);

        // Mode 3 — Week 3+: compare last two completed weeks
        if (week >= 3) {
            String insight = buildWeekComparisonInsight(familyId, current, week - 1, week - 2);
            if (insight != null) return insight;
        }

        // Mode 2 — Week 2: snapshot of the just-completed Week 1
        if (week >= 2) {
            String insight = buildWeekSnapshotInsight(familyId, current, 1);
            if (insight != null) return insight;
        }

        // Mode 1 / fallback — not enough current month data: show last month (up to 2 months back)
        for (int i = 1; i <= 2; i++) {
            String insight = buildMonthlyInsight(familyId, current.minusMonths(i));
            if (insight != null) return insight;
        }

        return null;
    }

    // Mode 2: one completed week — summarise what stood out and give a forward tip.
    private String buildWeekSnapshotInsight(Long familyId, YearMonth month, int week) {
        LocalDate[] range = weekRange(month, week);
        List<Object[]> categoryRows = receiptItemRepository.sumByCategory(familyId, range[0], range[1]);
        if (categoryRows.isEmpty()) return null;

        BigDecimal total = sumTotal(categoryRows);
        if (total.compareTo(BigDecimal.ZERO) == 0) return null;

        String monthName  = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String weekLabel  = "Week %d (%s %d–%d)".formatted(week, monthName, range[0].getDayOfMonth(), range[1].getDayOfMonth());
        String categories = buildCategoryBreakdown(categoryRows, total);

        String prompt = """
                You are a sharp, friendly family finance coach. Here is the %s spending summary:
                Total: %s EUR
                Categories: %s

                Write 2 sentences (max 45 words).
                Sentence 1: highlight the most notable finding with at least one concrete number (EUR or %%).
                Sentence 2: give one specific actionable tip for the rest of the month.

                Rules:
                - Only mention categories from the Categories list, using exact names.
                - Do not mention "Other food" as an advice target unless it is the only category.
                - Do not start with "Wow", "Great", "Amazing" or similar exclamations.
                - Do not wrap category names in quotes.
                - No markdown, no bullet points, no emojis.
                """.formatted(weekLabel, total.setScale(2, RoundingMode.HALF_UP), categories);

        String insight = geminiClient.generateText(prompt);
        if (insight != null) insight = insight.strip();
        log.debug("Week snapshot insight for family {} ({}): {}", familyId, weekLabel, insight);
        return insight;
    }

    // Mode 3: two completed weeks — compare them and give a forward tip.
    private String buildWeekComparisonInsight(Long familyId, YearMonth month, int laterWeek, int earlierWeek) {
        LocalDate[] laterRange   = weekRange(month, laterWeek);
        LocalDate[] earlierRange = weekRange(month, earlierWeek);

        List<Object[]> laterRows   = receiptItemRepository.sumByCategory(familyId, laterRange[0],   laterRange[1]);
        List<Object[]> earlierRows = receiptItemRepository.sumByCategory(familyId, earlierRange[0], earlierRange[1]);

        BigDecimal laterTotal   = sumTotal(laterRows);
        BigDecimal earlierTotal = sumTotal(earlierRows);
        if (laterTotal.compareTo(BigDecimal.ZERO) == 0 && earlierTotal.compareTo(BigDecimal.ZERO) == 0) return null;

        String monthName  = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        String laterLabel  = "Week %d (%s %d–%d)".formatted(laterWeek,  monthName, laterRange[0].getDayOfMonth(),  laterRange[1].getDayOfMonth());
        String earlierLabel = "Week %d (%s %d–%d)".formatted(earlierWeek, monthName, earlierRange[0].getDayOfMonth(), earlierRange[1].getDayOfMonth());

        BigDecimal diff = laterTotal.subtract(earlierTotal).setScale(2, RoundingMode.HALF_UP);
        String changeStr = (diff.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + diff + " EUR";

        String prompt = """
                You are a sharp, friendly family finance coach. Compare these two weeks of %s spending:

                %s: %s EUR | Categories: %s
                %s: %s EUR | Categories: %s
                Week-over-week change: %s

                Write 2 sentences (max 50 words).
                Sentence 1: state the most notable week-over-week change with concrete numbers.
                Sentence 2: give one specific actionable tip for the coming weeks.

                Rules:
                - Only mention categories from the lists above, using exact names.
                - Do not mention "Other food" as an advice target unless it is the only category.
                - Do not start with "Wow", "Great", "Amazing" or similar exclamations.
                - Do not wrap category names in quotes.
                - No markdown, no bullet points, no emojis.
                """.formatted(
                        month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        laterLabel,  laterTotal.setScale(2,  RoundingMode.HALF_UP), buildCategoryBreakdown(laterRows,   laterTotal),
                        earlierLabel, earlierTotal.setScale(2, RoundingMode.HALF_UP), buildCategoryBreakdown(earlierRows, earlierTotal),
                        changeStr);

        String insight = geminiClient.generateText(prompt);
        if (insight != null) insight = insight.strip();
        log.debug("Week comparison insight for family {} ({} vs {}): {}", familyId, laterLabel, earlierLabel, insight);
        return insight;
    }

    // Mode 1 / fallback: full month summary with weekly pattern.
    private String buildMonthlyInsight(Long familyId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to   = month.atEndOfMonth();

        List<Object[]> categoryRows = receiptItemRepository.sumByCategory(familyId, from, to);
        if (categoryRows.isEmpty()) return null;

        BigDecimal total = sumTotal(categoryRows);
        if (total.compareTo(BigDecimal.ZERO) == 0) return null;

        String monthName       = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        String categories      = buildCategoryBreakdown(categoryRows, total);
        String weeklyBreakdown = buildWeeklyBreakdown(familyId, month, from, to);

        String prompt = """
                You are a sharp, friendly family finance coach. Analyse this complete %s spending data and write 2 sentences (max 50 words total).

                Total spent: %s EUR
                Top categories: %s
                Weekly pattern: %s

                Choose the single most interesting angle — whichever is best supported by the data:
                  A) A dominant or surprisingly large category (mention EUR amount or %%),
                  B) A clear weekly spending spike (mention which week and the EUR amount).
                Only pick angle C (balanced/low spend as a positive) if ALL four weeks have meaningful spending.
                Sentence 1: state the finding with at least one concrete number (EUR or %%).
                Sentence 2: give one specific, actionable tip for next month tied directly to that finding.

                Rules:
                - Only mention categories from the Top categories list, using exact names.
                - Do not mention "Other food" as an advice target unless it is the only category.
                - Do not praise weeks with zero spending as an achievement — that just means no receipts were scanned.
                - Do not start with "Wow", "Great", "Amazing" or similar exclamations.
                - Do not wrap category names in quotes.
                - No markdown, no bullet points, no emojis.
                """.formatted(monthName, total.setScale(2, RoundingMode.HALF_UP), categories, weeklyBreakdown);

        String insight = geminiClient.generateText(prompt);
        if (insight != null) insight = insight.strip();
        log.debug("Monthly insight for family {} ({}): {}", familyId, month, insight);
        return insight;
    }

    // Groups daily totals into 4 fixed week buckets: days 1–7, 8–14, 15–21, 22–end.
    private String buildWeeklyBreakdown(Long familyId, YearMonth month, LocalDate from, LocalDate to) {
        List<Object[]> dailyRows = receiptItemRepository.sumByDate(familyId, from, to);

        java.util.Map<Integer, BigDecimal> byWeek = dailyRows.stream()
                .collect(Collectors.groupingBy(
                        r -> weekOfMonth((LocalDate) r[0]),
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> ((BigDecimal) r[1]).setScale(2, RoundingMode.HALF_UP),
                                BigDecimal::add)
                ));

        String monthName = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        int lastDay = month.lengthOfMonth();

        return List.of(
                "Week 1 (%s 1-7): %s EUR".formatted(monthName,    byWeek.getOrDefault(1, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)),
                "Week 2 (%s 8-14): %s EUR".formatted(monthName,   byWeek.getOrDefault(2, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)),
                "Week 3 (%s 15-21): %s EUR".formatted(monthName,  byWeek.getOrDefault(3, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)),
                "Week 4 (%s 22-%d): %s EUR".formatted(monthName, lastDay, byWeek.getOrDefault(4, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
        ).stream().collect(Collectors.joining(", "));
    }

    private String buildCategoryBreakdown(List<Object[]> categoryRows, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return "none";
        return categoryRows.stream()
                .sorted((l, r) -> ((BigDecimal) r[1]).compareTo((BigDecimal) l[1]))
                .limit(3)
                .filter(r -> ((BigDecimal) r[1])
                        .multiply(new BigDecimal("100"))
                        .divide(total, 0, RoundingMode.HALF_UP)
                        .compareTo(new BigDecimal("10")) >= 0)
                .map(r -> {
                    SpendingCategory cat = (SpendingCategory) r[0];
                    BigDecimal amt = ((BigDecimal) r[1]).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal pct = amt.multiply(new BigDecimal("100")).divide(total, 0, RoundingMode.HALF_UP);
                    return displayName(cat) + ": " + amt + " EUR (" + pct + "%)";
                })
                .collect(Collectors.joining(", "));
    }

    private BigDecimal sumTotal(List<Object[]> rows) {
        return rows.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Returns {from, to} for a given week number (1–4) within a month.
    private LocalDate[] weekRange(YearMonth month, int week) {
        int startDay = (week - 1) * 7 + 1;
        int endDay   = week < 4 ? startDay + 6 : month.lengthOfMonth();
        return new LocalDate[]{month.atDay(startDay), month.atDay(endDay)};
    }

    private static int weekOfMonth(LocalDate date) {
        int day = date.getDayOfMonth();
        if (day <= 7)  return 1;
        if (day <= 14) return 2;
        if (day <= 21) return 3;
        return 4;
    }

    private static String displayName(SpendingCategory category) {
        return switch (category) {
            case FOOD_PRODUCE -> "Produce";
            case FOOD_DAIRY   -> "Dairy";
            case FOOD_PROTEIN -> "Meat, fish and eggs";
            case FOOD_BAKERY  -> "Bakery";
            case FOOD_STAPLES -> "Pantry staples";
            case FOOD_SNACKS  -> "Snacks and sweets";
            case FOOD_DRINKS  -> "Drinks";
            case FOOD_ALCOHOL -> "Alcohol";
            case FOOD_OTHER   -> "Other food";
            case MEDICINE      -> "Medicine";
            case SUPPLEMENTS   -> "Supplements";
            case HYGIENE       -> "Hygiene";
            case HOUSEHOLD     -> "Household goods";
            case CLEANING      -> "Cleaning";
            case CLOTHING      -> "Clothing";
            case ENTERTAINMENT -> "Entertainment";
            case ELECTRONICS   -> "Electronics";
            case PETS          -> "Pet care";
            case CHILDREN      -> "Children";
            case TRANSPORT     -> "Transport";
            case OTHER         -> "Other";
        };
    }
}
