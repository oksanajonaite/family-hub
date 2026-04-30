package com.familyhub.dto.response.receipt;

import com.familyhub.entity.enums.ReceiptStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReceiptListResponse(
        Long id,
        String vendorName,
        LocalDate purchaseDate,
        BigDecimal totalAmount,
        ReceiptStatus status,
        LocalDateTime createdAt,
        String uploadedByName,
        int itemCount,
        List<ReceiptItemResponse> previewItems
) {}
