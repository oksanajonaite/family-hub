package com.familyhub.dto.gemini;

import com.familyhub.entity.enums.SpendingCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * The structured data that GeminiClient extracts from a receipt image.
 * All top-level fields are nullable — Gemini returns null when text is unreadable.
 * Category in Item is kept as a String so that unknown values from Gemini
 * fall back to OTHER instead of throwing a deserialization error.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiReceiptResult(
        String vendorName,
        String purchaseDate,   // "YYYY-MM-DD" or null — parsed by ReceiptParsingService
        BigDecimal totalAmount,
        List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String productName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String category    // raw string from Gemini — convert via spendingCategory()
    ) {
        /**
         * Converts the raw category string to our enum.
         * Falls back to OTHER if Gemini returns an unrecognized value.
         */
        public SpendingCategory spendingCategory() {
            if (category == null) return SpendingCategory.OTHER;
            try {
                return SpendingCategory.valueOf(category.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return SpendingCategory.OTHER;
            }
        }

        /** Safe quantity — defaults to 1 when Gemini omits it. */
        public BigDecimal safeQuantity() {
            return quantity != null ? quantity : BigDecimal.ONE;
        }
    }
}
