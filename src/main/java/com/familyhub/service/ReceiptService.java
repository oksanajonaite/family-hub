package com.familyhub.service;

import com.familyhub.dto.response.receipt.ReceiptListResponse;
import com.familyhub.dto.response.receipt.ReceiptResponse;
import com.familyhub.entity.Receipt;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.ReceiptStatus;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.RateLimitExceededException;
import com.familyhub.exception.ReceiptNotFoundException;
import com.familyhub.mapper.ReceiptMapper;
import com.familyhub.repository.ReceiptRepository;
import com.familyhub.repository.UserRepository;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Facade that orchestrates the full receipt upload pipeline:
 *   rate limit → validate → create entity → Gemini parse → save → return response.
 *
 * Other services (parsing, rate limiting) each own a single responsibility;
 * this service wires them together and owns nothing else.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final ReceiptParsingService receiptParsingService;
    private final ReceiptRateLimiterService rateLimiterService;
    private final ReceiptMapper receiptMapper;

    // Maximum photos allowed per single receipt upload (one long receipt = several photos)
    private static final int MAX_FILES_PER_UPLOAD = 5;

    /**
     * Processes one or more receipt photos and saves them as a single Receipt.
     * Supports multi-page receipts: pass 2–5 photos, results are merged automatically.
     *
     * Pipeline:
     * 1. Rate limit — reject if user has exceeded 5 uploads / hour
     * 2. Guards — user must have a family; 1–5 files required
     * 3. Persist Receipt with PROCESSING status (visible immediately in history)
     * 4. Parse each photo with Gemini; merge items + header fields across pages
     * 5. Save final state (DONE or FAILED) and return response DTO
     */
    // Evict all spending cache entries for this family when a new receipt is saved.
    // allEntries=true is intentional — we can't predict which months were affected
    // (purchase date from the receipt may differ from today).
    @Caching(evict = {
        @CacheEvict(value = "spendingByCategory",    allEntries = true),
        @CacheEvict(value = "spendingMonthlyTotals", allEntries = true)
    })
    @Transactional
    public ReceiptResponse uploadAndParse(List<MultipartFile> files, CustomUserDetails currentUser) {

        // 1. Rate limit — fail fast before touching the DB
        if (!rateLimiterService.tryConsume(currentUser.getId())) {
            throw new RateLimitExceededException(
                    "Receipt upload limit reached (5 per hour). Please try again later.");
        }

        // Drop empty file slots the browser may attach when no file is chosen
        List<MultipartFile> validFiles = files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();

        if (validFiles.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one photo.");
        }
        if (validFiles.size() > MAX_FILES_PER_UPLOAD) {
            throw new IllegalArgumentException(
                    "Maximum " + MAX_FILES_PER_UPLOAD + " photos per receipt.");
        }

        // 2. Load managed User entity within this transaction (needed for lazy family access)
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        if (user.getFamily() == null) {
            throw new FamilyNotFoundException("You must belong to a family to upload receipts.");
        }

        // 3. Persist with PROCESSING so the receipt appears in the history immediately
        Receipt receipt = Receipt.builder()
                .family(user.getFamily())
                .uploadedBy(user)
                .status(ReceiptStatus.PROCESSING)
                .build();
        receiptRepository.save(receipt);
        log.info("Receipt {} created (PROCESSING) — {} page(s), user {}",
                receipt.getId(), validFiles.size(), user.getId());

        // 4. Parse all pages and merge — updates receipt in-place; sets DONE or FAILED
        try {
            receiptParsingService.parseAndPopulate(receipt, validFiles);
        } catch (Exception e) {
            log.error("Unexpected error during parsing for receipt {}", receipt.getId(), e);
            receipt.setStatus(ReceiptStatus.FAILED);
        }

        // 5. @Transactional dirty checking will flush the updated entity automatically —
        //    no explicit save() needed. Keeping it here makes the intent explicit.
        log.info("Receipt {} finalised with status {}", receipt.getId(), receipt.getStatus());

        return receiptMapper.toDetailResponse(receipt);
    }

    /** Returns all receipts for the user's family, newest first, mapped for the list page. */
    @Transactional(readOnly = true)
    public List<ReceiptListResponse> getFamilyReceipts(Long familyId) {
        return receiptRepository.findAllByFamilyIdOrderByCreatedAtDesc(familyId)
                .stream()
                .map(receiptMapper::toListResponse)
                .toList();
    }

    /**
     * Returns a filtered + counted summary for the receipts list page.
     * Filtering and counting belong in the service, not the controller.
     *
     * @param familyId     the family to query
     * @param statusFilter optional status to filter by; null means "show all"
     */
    @Transactional(readOnly = true)
    public ReceiptSummary getReceiptSummary(Long familyId, ReceiptStatus statusFilter) {
        List<ReceiptListResponse> all = getFamilyReceipts(familyId);

        // Single pass — count all statuses at once instead of 3 separate stream iterations
        Map<ReceiptStatus, Long> countsByStatus = all.stream()
                .collect(Collectors.groupingBy(ReceiptListResponse::status, Collectors.counting()));

        List<ReceiptListResponse> filtered = (statusFilter != null)
                ? all.stream().filter(r -> r.status() == statusFilter).toList()
                : all;

        return new ReceiptSummary(
                filtered,
                all.size(),
                countsByStatus.getOrDefault(ReceiptStatus.DONE,       0L),
                countsByStatus.getOrDefault(ReceiptStatus.FAILED,     0L),
                countsByStatus.getOrDefault(ReceiptStatus.PROCESSING, 0L)
        );
    }

    /**
     * Summary DTO for the receipts list page —
     * carries the filtered list and per-status counts in one object.
     */
    public record ReceiptSummary(
            List<ReceiptListResponse> receipts,
            long totalCount,
            long doneCount,
            long failedCount,
            long processingCount
    ) {}

    /** Returns a single receipt — throws if it doesn't belong to the given family (security guard). */
    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(Long receiptId, Long familyId) {
        Receipt receipt = receiptRepository.findByIdAndFamilyId(receiptId, familyId)
                .orElseThrow(() -> new ReceiptNotFoundException(
                        "Receipt not found or does not belong to your family."));
        return receiptMapper.toDetailResponse(receipt);
    }

    /**
     * Retries parsing for a FAILED receipt by re-scanning new photos.
     * Allowed only once per receipt (retryCount == 0) to prevent abuse.
     *
     * Pipeline:
     * 1. Guard — receipt must be FAILED and not yet retried
     * 2. Clear old items + reset header fields
     * 3. Set status → PROCESSING, increment retryCount
     * 4. Re-run Gemini parsing on the new photos
     */
    @Caching(evict = {
        @CacheEvict(value = "spendingByCategory",    allEntries = true),
        @CacheEvict(value = "spendingMonthlyTotals", allEntries = true)
    })
    @Transactional
    public ReceiptResponse retryParsing(
            Long receiptId, Long familyId,
            List<MultipartFile> files, CustomUserDetails currentUser) {

        Receipt receipt = receiptRepository.findByIdAndFamilyId(receiptId, familyId)
                .orElseThrow(() -> new ReceiptNotFoundException(
                        "Receipt not found or does not belong to your family."));

        if (receipt.getStatus() != ReceiptStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED receipts can be retried.");
        }
        if (receipt.getRetryCount() >= 1) {
            throw new IllegalStateException(
                    "This receipt has already been retried once.");
        }

        // Validate new files (same rules as upload, minus rate-limit)
        List<MultipartFile> validFiles = files.stream()
                .filter(f -> f != null && !f.isEmpty())
                .toList();
        if (validFiles.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one photo.");
        }
        if (validFiles.size() > MAX_FILES_PER_UPLOAD) {
            throw new IllegalArgumentException(
                    "Maximum " + MAX_FILES_PER_UPLOAD + " photos per receipt.");
        }

        // Clear old data — orphanRemoval removes items from DB on flush
        receipt.getItems().clear();
        receipt.setVendorName(null);
        receipt.setPurchaseDate(null);
        receipt.setTotalAmount(null);
        receipt.setStatus(ReceiptStatus.PROCESSING);
        receipt.setRetryCount(receipt.getRetryCount() + 1);
        receiptRepository.save(receipt);
        log.info("Retry started for receipt {} — {} page(s), user {}",
                receipt.getId(), validFiles.size(), currentUser.getId());

        try {
            receiptParsingService.parseAndPopulate(receipt, validFiles);
        } catch (Exception e) {
            log.error("Unexpected error during retry parsing for receipt {}", receipt.getId(), e);
            receipt.setStatus(ReceiptStatus.FAILED);
        }

        log.info("Receipt {} retry finalised with status {}", receipt.getId(), receipt.getStatus());
        return receiptMapper.toDetailResponse(receipt);
    }

    /** Deletes a receipt and all its items (cascade). Family ID is the security guard. */
    @Transactional
    public void deleteReceipt(Long receiptId, Long familyId) {
        Receipt receipt = receiptRepository.findByIdAndFamilyId(receiptId, familyId)
                .orElseThrow(() -> new ReceiptNotFoundException(
                        "Receipt not found or does not belong to your family."));
        receiptRepository.delete(receipt);
        log.info("Receipt {} deleted from family {}", receiptId, familyId);
    }
}
