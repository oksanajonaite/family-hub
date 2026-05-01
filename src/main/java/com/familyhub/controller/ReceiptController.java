package com.familyhub.controller;

import com.familyhub.dto.response.receipt.ReceiptResponse;
import com.familyhub.entity.enums.ReceiptStatus;
import com.familyhub.exception.RateLimitExceededException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.ReceiptService;
import com.familyhub.service.ReceiptService.ReceiptSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    // FamilyRequiredInterceptor guards /receipts/** — no null checks needed here.

    /**
     * Lists all family receipts, optionally filtered by status.
     * Filtering and counts are delegated to ReceiptService.
     */
    @GetMapping
    public String index(
            @RequestParam(required = false) String status,
            Model model,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        ReceiptStatus statusFilter = parseStatus(status);
        ReceiptSummary summary = receiptService.getReceiptSummary(
                currentUser.getFamilyId(), statusFilter);

        model.addAttribute("receipts",        summary.receipts());
        model.addAttribute("totalCount",      summary.totalCount());
        model.addAttribute("doneCount",       summary.doneCount());
        model.addAttribute("failedCount",     summary.failedCount());
        model.addAttribute("processingCount", summary.processingCount());
        model.addAttribute("selectedStatus",  status);
        model.addAttribute("statuses",        ReceiptStatus.values());

        return "receipts/index";
    }

    /** Shows the full detail view for one receipt — all items, categories, prices. */
    @GetMapping("/{id}")
    public String detail(
            @PathVariable Long id,
            Model model,
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        model.addAttribute("receipt",
                receiptService.getReceipt(id, currentUser.getFamilyId()));
        return "receipts/detail";
    }

    /** Shows the upload form. */
    @GetMapping("/upload")
    public String uploadForm() {
        return "receipts/upload";
    }

    /**
     * Processes the uploaded receipt image.
     * On success: flash message with item count.
     * On FAILED parse: flash warning — receipt is still saved, user can retry.
     */
    @PostMapping("/upload")
    public String upload(
            @RequestParam("file") List<MultipartFile> files,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            ReceiptResponse receipt = receiptService.uploadAndParse(files, currentUser);

            if (receipt.status() == ReceiptStatus.DONE) {
                int itemCount = receipt.items() != null ? receipt.items().size() : 0;
                addSuccess(redirectAttributes, "Receipt scanned successfully! " + itemCount + " item"
                        + (itemCount == 1 ? "" : "s") + " extracted.");
            } else {
                addError(redirectAttributes, "Receipt saved but couldn't be read automatically. "
                        + "Try uploading a clearer photo.");
            }

        } catch (RateLimitExceededException e) {
            addError(redirectAttributes, e.getMessage());
        } catch (Exception e) {
            log.error("Receipt upload failed for user {}", currentUser.getId(), e);
            addError(redirectAttributes, "Something went wrong while uploading. Please try again.");
        }

        return "redirect:/receipts";
    }

    /** Shows the retry upload form for a specific FAILED receipt. */
    @GetMapping("/{id}/retry")
    public String retryForm(
            @PathVariable Long id,
            Model model,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            ReceiptResponse receipt = receiptService.getReceipt(id, currentUser.getFamilyId());

            if (receipt.status() != com.familyhub.entity.enums.ReceiptStatus.FAILED) {
                addError(redirectAttributes, "Only failed receipts can be retried.");
                return "redirect:/receipts";
            }
            if (receipt.retryCount() >= 3) {
                addError(redirectAttributes, "This receipt has reached the maximum number of retries (3).");
                return "redirect:/receipts";
            }

            model.addAttribute("receipt", receipt);
            return "receipts/retry";

        } catch (Exception e) {
            addError(redirectAttributes, "Receipt not found.");
            return "redirect:/receipts";
        }
    }

    /** Processes the retry upload — re-parses a FAILED receipt with new photos. */
    @PostMapping("/{id}/retry")
    public String retry(
            @PathVariable Long id,
            @RequestParam("file") List<MultipartFile> files,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            ReceiptResponse receipt = receiptService.retryParsing(
                    id, currentUser.getFamilyId(), files, currentUser);

            if (receipt.status() == com.familyhub.entity.enums.ReceiptStatus.DONE) {
                int itemCount = receipt.items() != null ? receipt.items().size() : 0;
                addSuccess(redirectAttributes, "Retry successful! " + itemCount + " item"
                        + (itemCount == 1 ? "" : "s") + " extracted.");
            } else {
                addError(redirectAttributes, "Retry saved but still couldn't be read. "
                        + "The receipt photo may be too unclear.");
            }

        } catch (IllegalStateException | IllegalArgumentException e) {
            addError(redirectAttributes, e.getMessage());
        } catch (Exception e) {
            log.error("Retry failed for receipt {} user {}", id, currentUser.getId(), e);
            addError(redirectAttributes, "Something went wrong during retry. Please try again.");
        }

        return "redirect:/receipts";
    }

    /**
     * Deletes a receipt — PARENT only (enforced by @PreAuthorize).
     * findByIdAndFamilyId in the service acts as a security guard against cross-family access.
     */
    @PreAuthorize("hasRole('PARENT')")
    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes) {

        try {
            receiptService.deleteReceipt(id, currentUser.getFamilyId());
            addSuccess(redirectAttributes, "Receipt deleted.");
        } catch (Exception e) {
            log.error("Could not delete receipt {} for user {}", id, currentUser.getId(), e);
            addError(redirectAttributes, "Could not delete receipt.");
        }

        return "redirect:/receipts";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void addSuccess(RedirectAttributes ra, String message) {
        ra.addFlashAttribute("successMessage", message);
    }

    private void addError(RedirectAttributes ra, String message) {
        ra.addFlashAttribute("errorMessage", message);
    }

    /**
     * Safely parses the status request parameter to a ReceiptStatus enum.
     * Returns null (= no filter) for missing or unrecognised values.
     */
    private ReceiptStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return ReceiptStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
