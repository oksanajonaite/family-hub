package com.familyhub.dto.response.spending;

import com.familyhub.entity.enums.SpendingCategory;

import java.math.BigDecimal;

/**
 * Spending total for one category in a given period.
 * Used by the spending statistics page.
 */
public record CategorySpendingEntry(
        SpendingCategory category,
        BigDecimal total
) {}
