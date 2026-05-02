package com.familyhub.controller;

import com.familyhub.dto.response.spending.CategorySpendingEntry;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.SpendingService;
import com.familyhub.service.SpendingService.ChartData;
import com.familyhub.service.SpendingService.MonthlyTotal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Displays the monthly spending breakdown by category with donut and bar chart data.
 * Guarded by {@link com.familyhub.interceptor.FamilyRequiredInterceptor} — no family null-check needed.
 */
@Controller
@RequestMapping("/spending")
@RequiredArgsConstructor
public class SpendingController {

    private static final int BAR_CHART_MONTHS = 6;

    private final SpendingService spendingService;

    // FamilyRequiredInterceptor guards /spending/** — no null check needed here.

    @GetMapping
    public String index(
            @RequestParam(required = false) String month,
            Model model,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        YearMonth selectedMonth = parseMonth(month);
        Long familyId = currentUser.getFamilyId();

        List<CategorySpendingEntry> entries =
                spendingService.getMonthlyCategorySpending(familyId, selectedMonth);
        BigDecimal monthTotal  = spendingService.getMonthlyTotal(entries);
        List<MonthlyTotal> monthlyTotals =
                spendingService.getMonthlyTotals(familyId, selectedMonth, BAR_CHART_MONTHS);

        List<BigDecimal> percentages = spendingService.calculatePercentages(entries, monthTotal);
        ChartData chartData          = spendingService.buildChartData(entries, monthlyTotals);

        model.addAttribute("selectedMonth",  selectedMonth);
        model.addAttribute("prevMonth",      selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth",      selectedMonth.plusMonths(1));
        model.addAttribute("isCurrentMonth", selectedMonth.equals(YearMonth.now()));
        model.addAttribute("entries",        entries);
        model.addAttribute("percentages",    percentages);
        model.addAttribute("monthTotal",     monthTotal);
        model.addAttribute("hasData",        !entries.isEmpty());
        model.addAttribute("donutLabels",    chartData.donutLabels());
        model.addAttribute("donutValues",    chartData.donutValues());
        model.addAttribute("barLabels",      chartData.barLabels());
        model.addAttribute("barValues",      chartData.barValues());

        return "spending/index";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Parses the month request parameter; defaults to current month on missing or invalid input. */
    private YearMonth parseMonth(String param) {
        if (param == null || param.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(param); // expects "2026-04"
        } catch (Exception e) {
            return YearMonth.now();
        }
    }
}
