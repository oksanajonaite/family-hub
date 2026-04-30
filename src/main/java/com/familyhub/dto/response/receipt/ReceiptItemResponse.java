package com.familyhub.dto.response.receipt;

import com.familyhub.entity.enums.SpendingCategory;

import java.math.BigDecimal;

public record ReceiptItemResponse(
        Long id,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        SpendingCategory category,
        // Computed field: quantity × unitPrice — convenient for Thymeleaf templates
        BigDecimal lineTotal
) {}
