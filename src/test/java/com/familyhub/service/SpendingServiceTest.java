package com.familyhub.service;

import com.familyhub.dto.response.spending.CategorySpendingEntry;
import com.familyhub.entity.enums.SpendingCategory;
import com.familyhub.service.SpendingService.MonthlyTotal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.familyhub.repository.ReceiptItemRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SpendingServiceTest {

    @Mock private ReceiptItemRepository receiptItemRepository;
    private SpendingService spendingService;

    // ObjectMapper reikalingas buildChartData — kuriame servisą rankiniu būdu,
    // nes Mockito @InjectMocks nesupranta ne-mock priklausomybių.
    // @BeforeEach užtikrina, kad @Mock laukai jau inicializuoti prieš konstruktorių.
    @BeforeEach
    void setUp() {
        spendingService = new SpendingService(receiptItemRepository, new ObjectMapper());
    }

    // ── Pagalbiniai metodai ───────────────────────────────────────────────────

    private CategorySpendingEntry entry(String amount) {
        return new CategorySpendingEntry(SpendingCategory.FOOD_OTHER, new BigDecimal(amount));
    }

    private MonthlyTotal monthTotal(YearMonth month, String amount) {
        return new MonthlyTotal(month, new BigDecimal(amount));
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // getMonthlyTotal susumuoja visų kategorijų sumas teisingai.
    @Test
    void getMonthlyTotal_sumsAllEntries() {
        List<CategorySpendingEntry> entries = List.of(
                entry("10.00"), entry("25.50"), entry("4.50")
        );

        BigDecimal total = spendingService.getMonthlyTotal(entries);

        assertEquals(new BigDecimal("40.00"), total);
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Tuščias sąrašas → 0.00 (ne NullPointerException ar klaida).
    @Test
    void getMonthlyTotal_whenNoEntries_returnsZero() {
        BigDecimal total = spendingService.getMonthlyTotal(List.of());

        assertEquals(new BigDecimal("0.00"), total);
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // calculatePercentages: €65.53 iš €83.98 = 78.0% (1 skaitmuo po kablelio).
    @Test
    void calculatePercentages_calculatesCorrectly() {
        List<CategorySpendingEntry> entries = List.of(
                new CategorySpendingEntry(SpendingCategory.FOOD_OTHER, new BigDecimal("65.53")),
                new CategorySpendingEntry(SpendingCategory.FOOD_DRINKS, new BigDecimal("18.45"))
        );
        BigDecimal total = new BigDecimal("83.98");

        List<BigDecimal> percentages = spendingService.calculatePercentages(entries, total);

        assertEquals(new BigDecimal("78.0"), percentages.get(0));
        assertEquals(new BigDecimal("22.0"), percentages.get(1));
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Kai total = 0 — visi procentai turi būti 0 (ne ArithmeticException iš dalybos).
    // Galimas atvejis: mėnuo be kvitų, bet puslapis vis tiek rodomas.
    @Test
    void calculatePercentages_whenTotalIsZero_returnsAllZeros() {
        List<CategorySpendingEntry> entries = List.of(entry("0.00"), entry("0.00"));

        List<BigDecimal> percentages = spendingService.calculatePercentages(
                entries, BigDecimal.ZERO);

        percentages.forEach(p -> assertEquals(BigDecimal.ZERO, p));
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // getMonthlyTotals nupjauna pradinius nulinius mėnesius.
    // Pvz.: [Nov=0, Dec=0, Jan=0, Feb=0, Mar=0, Apr=84] → [Apr=84]
    // Bar chart nesiflokuoja su tuščia erdve kairėje.
    @Test
    void getMonthlyTotals_trimLeadingZeroMonths() {
        YearMonth apr = YearMonth.of(2026, 4);

        // Stub: DB grąžina tik Apr duomenis
        List<Object[]> rows = Collections.singletonList(
                new Object[]{2026, 4, new BigDecimal("84.00")});
        org.mockito.Mockito.when(receiptItemRepository.sumMonthlyTotals(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
            .thenReturn(rows);

        List<MonthlyTotal> result = spendingService.getMonthlyTotals(1L, apr, 6);

        // Nov-Mar nupjauti, lieka tik Apr
        assertEquals(1, result.size());
        assertEquals(apr, result.get(0).month());
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────
    // Kai visi mėnesiai nuliniai — grąžinamas bent einamasis mėnuo.
    // Chart niekada nebus visiškai tuščias.
    @Test
    void getMonthlyTotals_whenAllZero_returnsCurrentMonthOnly() {
        YearMonth apr = YearMonth.of(2026, 4);

        List<Object[]> empty = new ArrayList<>();
        org.mockito.Mockito.when(receiptItemRepository.sumMonthlyTotals(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
            .thenReturn(empty);

        List<MonthlyTotal> result = spendingService.getMonthlyTotals(1L, apr, 6);

        assertEquals(1, result.size());
        assertEquals(apr, result.get(0).month());
        assertEquals(BigDecimal.ZERO.setScale(2), result.get(0).total());
    }

    // ── Test 7 ────────────────────────────────────────────────────────────────
    // buildChartData: labels ir values teisingai serializuojami į JSON.
    // Donut labels turi būti JSON array su kategorijų pavadinimais.
    @Test
    void buildChartData_generatesCorrectJsonArrays() {
        List<CategorySpendingEntry> entries = List.of(
                new CategorySpendingEntry(SpendingCategory.FOOD_OTHER, new BigDecimal("65.53"))
        );
        List<MonthlyTotal> totals = List.of(
                monthTotal(YearMonth.of(2026, 4), "65.53")
        );

        SpendingService.ChartData chart = spendingService.buildChartData(entries, totals);

        assertEquals("[\"FOOD OTHER\"]", chart.donutLabels());
        assertEquals("[65.53]",          chart.donutValues());
        assertTrue(chart.barLabels().contains("Apr 2026"));
        assertEquals("[65.53]",          chart.barValues());
    }
}
