package com.familyhub.service;

import com.familyhub.dto.gemini.GeminiReceiptResult;
import com.familyhub.entity.Receipt;
import com.familyhub.entity.ReceiptItem;
import com.familyhub.entity.enums.ReceiptStatus;
import com.familyhub.exception.GeminiParsingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calls GeminiClient for each uploaded photo, merges results across pages,
 * and populates the Receipt entity in-place.
 *
 * Multi-page merge strategy:
 *   vendorName   → first non-null (top of first page)
 *   purchaseDate → first non-null
 *   totalAmount  → last non-null  (grand total appears on the last page)
 *   items        → union of all pages
 *
 * SRP: this class owns only the parse + merge step.
 * ReceiptService (facade) owns the full upload pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptParsingService {

    private final GeminiClient geminiClient;

    /**
     * Parses one or more receipt photos and populates {@code receipt} in-place.
     * Sets status to DONE if at least one page was parsed, FAILED if all pages failed.
     *
     * @param receipt managed entity to populate — must already be persisted (PROCESSING)
     * @param files   one or more photos of the same receipt (max 5)
     */
    public void parseAndPopulate(Receipt receipt, List<MultipartFile> files) {
        List<GeminiReceiptResult> results = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            try {
                byte[] bytes = file.getBytes();
                String mimeType = resolveMimeType(file);
                GeminiReceiptResult result = geminiClient.parseReceipt(bytes, mimeType);
                results.add(result);
                log.debug("Parsed page {}/{} for receipt {}", i + 1, files.size(), receipt.getId());
            } catch (GeminiParsingException | IOException e) {
                // One failed page does not abort the whole receipt — try remaining pages.
                // Pass `e` as last arg so Slf4j prints the full stack trace including cause.
                log.warn("Failed to parse page {}/{} of receipt {}",
                        i + 1, files.size(), receipt.getId(), e);
            }
        }

        if (results.isEmpty()) {
            log.error("All {} page(s) failed for receipt {}", files.size(), receipt.getId());
            receipt.setStatus(ReceiptStatus.FAILED);
            return;
        }

        // ── Merge header fields ───────────────────────────────────────────
        receipt.setVendorName(
                results.stream().map(GeminiReceiptResult::vendorName)
                        .filter(Objects::nonNull).findFirst().orElse(null));

        // Fall back to today if Gemini could not read the date — ensures receipt
        // always appears in a spending month instead of being silently excluded.
        receipt.setPurchaseDate(
                results.stream().map(r -> parseDate(r.purchaseDate()))
                        .filter(Objects::nonNull).findFirst().orElse(LocalDate.now()));

        // Grand total is on the last receipt page — take the last non-null value
        receipt.setTotalAmount(
                results.stream().map(GeminiReceiptResult::totalAmount)
                        .filter(Objects::nonNull)
                        .reduce((first, second) -> second).orElse(null));

        // ── Merge items from all pages ────────────────────────────────────
        // Defensive null check — entity should always initialise items as an empty list,
        // but guard here to avoid NPE if the collection is ever lazily null.
        if (receipt.getItems() == null) {
            receipt.setItems(new ArrayList<>());
        }

        results.stream()
                .filter(r -> r.items() != null)
                .flatMap(r -> r.items().stream())
                .filter(i -> i.productName() != null && i.unitPrice() != null)
                .map(i -> ReceiptItem.builder()
                        .receipt(receipt)
                        .productName(i.productName().trim())
                        .quantity(i.safeQuantity())
                        .unitPrice(i.unitPrice())
                        .category(i.spendingCategory())
                        .build())
                .forEach(item -> receipt.getItems().add(item));

        receipt.setStatus(ReceiptStatus.DONE);
        log.info("Receipt {} — merged {} page(s), {} items total",
                receipt.getId(), results.size(), receipt.getItems().size());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveMimeType(MultipartFile file) {
        String ct = file.getContentType();
        if (ct != null && !ct.isBlank()) return ct;
        log.warn("File '{}' has no Content-Type — falling back to image/jpeg", file.getOriginalFilename());
        return "image/jpeg";
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (DateTimeParseException e) {
            log.warn("Could not parse purchaseDate from Gemini: '{}'", dateStr);
            return null;
        }
    }
}
