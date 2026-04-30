package com.familyhub.service;

import com.familyhub.dto.response.spending.CategorySpendingEntry;
import com.familyhub.entity.enums.SpendingCategory;
import com.familyhub.repository.ReceiptItemRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Aggregates receipt item data into spending statistics for the spending page.
 * All queries filter by family and only include DONE receipts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpendingService {

    private final ReceiptItemRepository receiptItemRepository;
    private final ObjectMapper objectMapper;

    /**
     * Returns spending per category for the given month, sorted by total descending.
     * Categories with zero spending are excluded.
     * Cached per family+month — evicted by ReceiptService when a new receipt is saved.
     */
    @Cacheable(value = "spendingByCategory", key = "#familyId + '_' + #month")
    public List<CategorySpendingEntry> getMonthlyCategorySpending(Long familyId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to   = month.atEndOfMonth();

        List<Object[]> rows = receiptItemRepository.sumByCategory(familyId, from, to);

        return rows.stream()
                .map(row -> new CategorySpendingEntry(
                        (SpendingCategory) row[0],
                        ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP)
                ))
                .sorted(Comparator.comparing(CategorySpendingEntry::total).reversed())
                .toList();
    }

    /**
     * Returns the grand total spent across all categories in the given month.
     */
    public BigDecimal getMonthlyTotal(List<CategorySpendingEntry> entries) {
        return entries.stream()
                .map(CategorySpendingEntry::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Returns spending totals for the last N months (for the bar chart).
     * Cached per family+currentMonth — evicted by ReceiptService when a new receipt is saved.
     *
     * @param months number of past months to include (e.g. 6)
     */
    @Cacheable(value = "spendingMonthlyTotals", key = "#familyId + '_' + #currentMonth")
    public List<MonthlyTotal> getMonthlyTotals(Long familyId, YearMonth currentMonth, int months) {
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        LocalDate from = firstMonth.atDay(1);
        LocalDate to = currentMonth.atEndOfMonth();

        Map<YearMonth, BigDecimal> totalsByMonth = receiptItemRepository.sumMonthlyTotals(familyId, from, to)
                .stream()
                .collect(Collectors.toMap(
                        row -> YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                        row -> ((BigDecimal) row[2]).setScale(2, RoundingMode.HALF_UP)
                ));

        List<MonthlyTotal> all = IntStream.range(0, months)
                .mapToObj(i -> currentMonth.minusMonths(months - 1 - i))
                .map(month -> new MonthlyTotal(
                        month,
                        totalsByMonth.getOrDefault(month, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                ))
                .toList();

        // Drop leading zero months — show history only from the first month with spending.
        // Always keep at least the current month so the chart is never fully empty.
        List<MonthlyTotal> trimmed = all.stream()
                .dropWhile(t -> t.total().compareTo(BigDecimal.ZERO) == 0)
                .toList();
        return trimmed.isEmpty() ? all.subList(all.size() - 1, all.size()) : trimmed;
    }

    /**
     * Calculates each category's share of the monthly total as a percentage.
     * Returns 0 for all entries when total is zero (avoids division by zero).
     */
    public List<BigDecimal> calculatePercentages(List<CategorySpendingEntry> entries,
                                                  BigDecimal monthTotal) {
        return entries.stream()
                .map(e -> monthTotal.compareTo(BigDecimal.ZERO) == 0
                        ? BigDecimal.ZERO
                        : e.total()
                            .multiply(new BigDecimal("100"))
                            .divide(monthTotal, 1, RoundingMode.HALF_UP))
                .toList();
    }

    /**
     * Builds pre-serialised JSON arrays for Chart.js — keeps serialisation out of the controller.
     */
    public ChartData buildChartData(List<CategorySpendingEntry> entries,
                                     List<MonthlyTotal> monthlyTotals) {
        String donutLabels = toJsonStringArray(entries.stream()
                .map(e -> e.category().name().replace('_', ' '))
                .toList());
        String donutValues = toJsonNumberArray(entries.stream()
                .map(e -> e.total().toPlainString())
                .toList());
        String barLabels = toJsonStringArray(monthlyTotals.stream()
                .map(t -> t.month().getMonth()
                        .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        + " " + t.month().getYear())
                .toList());
        String barValues = toJsonNumberArray(monthlyTotals.stream()
                .map(t -> t.total().toPlainString())
                .toList());

        return new ChartData(donutLabels, donutValues, barLabels, barValues);
    }

    /**
     * Simple record pairing a month with its total spending — used for the bar chart.
     */
    public record MonthlyTotal(YearMonth month, BigDecimal total) {}

    /**
     * Chart data pre-serialised as JSON strings so Thymeleaf can safely inline them
     * in data-* attributes without escaping issues.
     */
    public record ChartData(
            String donutLabels,
            String donutValues,
            String barLabels,
            String barValues
    ) {}

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Serializes a list of strings to a JSON array using Jackson.
     * Safer than manual string concatenation — handles quotes, escaping, and
     * special characters automatically.
     */
    private String toJsonStringArray(List<String> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize string list to JSON: {}", items, e);
            return "[]";
        }
    }

    /**
     * Serializes a list of numeric strings to a JSON number array: [1.23,4.56].
     * Values come from BigDecimal.toPlainString() — safe to embed directly without quoting.
     */
    private String toJsonNumberArray(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }
}
