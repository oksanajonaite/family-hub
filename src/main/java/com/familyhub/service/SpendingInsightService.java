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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a short Gemini-powered spending insight for the dashboard widget.
 * Result is cached per family+month and evicted when a new receipt is parsed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingInsightService {

    private final ReceiptItemRepository receiptItemRepository;
    private final GeminiClient geminiClient;

    /**
     * Returns a 1–2 sentence spending insight for the most recent month that has data
     * (tries current month first, then falls back up to 2 months back).
     * Returns null only when there is genuinely no spending data in the last 3 months.
     */
    @Cacheable(value = "spendingInsight", key = "#familyId")
    public String getInsight(Long familyId) {
        for (int i = 0; i <= 2; i++) {
            YearMonth month = YearMonth.now().minusMonths(i);
            String insight = buildInsightForMonth(familyId, month);
            if (insight != null) {
                return insight;
            }
        }
        return null;
    }

    private String buildInsightForMonth(Long familyId, YearMonth month) {
        // Current month with < 7 days is too early for a meaningful weekly pattern —
        // the caller will fall back to the previous month instead.
        if (month.equals(YearMonth.now()) && LocalDate.now().getDayOfMonth() < 7) {
            return null;
        }

        LocalDate from = month.atDay(1);
        LocalDate to   = month.atEndOfMonth();

        List<Object[]> categoryRows = receiptItemRepository.sumByCategory(familyId, from, to);
        if (categoryRows.isEmpty()) return null;

        BigDecimal total = categoryRows.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) == 0) return null;

        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        String categoryBreakdown = categoryRows.stream()
                .map(r -> {
                    SpendingCategory cat = (SpendingCategory) r[0];
                    BigDecimal amt = ((BigDecimal) r[1]).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal pct = amt.multiply(new BigDecimal("100"))
                            .divide(total, 0, RoundingMode.HALF_UP);
                    return cat.name().replace('_', ' ') + ": " + amt + " EUR (" + pct + "%)";
                })
                .collect(Collectors.joining(", "));

        String weeklyBreakdown = buildWeeklyBreakdown(familyId, month, from, to);

        String prompt = """
                You are a friendly family finance assistant. Given %s spending data:
                Categories: %s
                Weekly: %s
                Write 1-2 short friendly sentences (max 35 words).
                Rules:
                - Never mention FOOD OTHER - it is too vague. Pick a specific category like protein, dairy, snacks.
                - The month is already over, so tips must be forward-looking (next month, not "plan for week 4").
                - Focus on the weekly pattern or a specific subcategory. Give one practical forward-looking tip.
                No bullet points, no markdown.
                """.formatted(monthName, categoryBreakdown, weeklyBreakdown);

        String insight = geminiClient.generateText(prompt);
        if (insight != null) insight = insight.strip();
        log.debug("Generated spending insight for family {} ({}): {}", familyId, month, insight);
        return insight;
    }

    // Groups daily totals into 4 fixed week buckets: days 1–7, 8–14, 15–21, 22–end.
    private String buildWeeklyBreakdown(Long familyId, YearMonth month, LocalDate from, LocalDate to) {
        List<Object[]> dailyRows = receiptItemRepository.sumByDate(familyId, from, to);

        Map<Integer, BigDecimal> byWeek = dailyRows.stream()
                .collect(Collectors.groupingBy(
                        r -> weekOfMonth((LocalDate) r[0]),
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> ((BigDecimal) r[1]).setScale(2, RoundingMode.HALF_UP),
                                BigDecimal::add)
                ));

        String monthName = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        int lastDay = month.lengthOfMonth();

        return List.of(
                "Week 1 (%s 1-7): %s EUR".formatted(monthName, byWeek.getOrDefault(1, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)),
                "Week 2 (%s 8-14): %s EUR".formatted(monthName, byWeek.getOrDefault(2, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)),
                "Week 3 (%s 15-21): %s EUR".formatted(monthName, byWeek.getOrDefault(3, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)),
                "Week 4 (%s 22-%d): %s EUR".formatted(monthName, lastDay, byWeek.getOrDefault(4, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
        ).stream().collect(Collectors.joining(", "));
    }

    private static int weekOfMonth(LocalDate date) {
        int day = date.getDayOfMonth();
        if (day <= 7)  return 1;
        if (day <= 14) return 2;
        if (day <= 21) return 3;
        return 4;
    }
}
