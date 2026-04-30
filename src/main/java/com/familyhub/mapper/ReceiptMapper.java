package com.familyhub.mapper;

import com.familyhub.dto.response.receipt.ReceiptItemResponse;
import com.familyhub.dto.response.receipt.ReceiptListResponse;
import com.familyhub.dto.response.receipt.ReceiptResponse;
import com.familyhub.entity.Receipt;
import com.familyhub.entity.ReceiptItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ReceiptMapper {

    // DETAIL RESPONSE: maps the full Receipt including all items.
    @Mapping(target = "uploadedByName",
            expression = "java(receipt.getUploadedBy().getDisplayName())")
    ReceiptResponse toDetailResponse(Receipt receipt);

    // LIST RESPONSE: keeps the index page lighter by exposing only preview items and item count.
    default ReceiptListResponse toListResponse(Receipt receipt) {
        List<ReceiptItemResponse> previewItems = receipt.getItems().stream()
                .limit(5)
                .map(this::toItemResponse)
                .toList();

        return new ReceiptListResponse(
                receipt.getId(),
                receipt.getVendorName(),
                receipt.getPurchaseDate(),
                receipt.getTotalAmount(),
                receipt.getStatus(),
                receipt.getCreatedAt(),
                receipt.getUploadedBy().getDisplayName(),
                receipt.getItems().size(),
                previewItems
        );
    }

    // ReceiptItem → ReceiptItemResponse.
    // lineTotal is computed here (quantity × unitPrice) so Thymeleaf templates stay logic-free.
    @Mapping(target = "lineTotal",
            expression = "java(item.getQuantity().multiply(item.getUnitPrice()))")
    ReceiptItemResponse toItemResponse(ReceiptItem item);

    // MapStruct calls toItemResponse for each element — declared explicitly for clarity.
    List<ReceiptItemResponse> toItemResponseList(List<ReceiptItem> items);
}
