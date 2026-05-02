package com.familyhub.controller;

import com.familyhub.dto.request.profile.ChangePasswordRequest;
import com.familyhub.dto.request.profile.UpdateProfileRequest;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.ProfileService;
import com.familyhub.util.PhotoUploadValidator;
import com.familyhub.util.SecurityContextHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Handles profile updates for the currently logged-in user: avatar upload, display name, and password.
 * After each change the security context is refreshed so updates appear in the navbar immediately.
 * Avatar uses a separate endpoint because file upload requires multipart/form-data
 * while profile update uses application/x-www-form-urlencoded.
 */
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final SecurityContextHelper securityContextHelper;

    // Separate endpoint from /profile/update because file upload requires multipart/form-data,
    // while the regular profile update uses application/x-www-form-urlencoded.
    // Mixing MultipartFile into an existing @ModelAttribute record is not straightforward.
    // After upload, the security context is refreshed so the new avatarKey is visible immediately in the session.
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        // Validation delegated to PhotoUploadValidator — shared with PetController and FamilyMemberController
        Optional<String> validationError = PhotoUploadValidator.validate(file);
        if (validationError.isPresent()) {
            redirectAttributes.addFlashAttribute("profileError", validationError.get());
            return "redirect:/family";
        }

        try {
            profileService.uploadAvatar(currentUser.getId(), file);
            // Refresh session so the new avatarKey is picked up by the navbar immediately
            securityContextHelper.refresh(currentUser.getEmail());
            redirectAttributes.addFlashAttribute("successMessage", "Profile photo updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("profileError", "Photo upload failed. Please try again.");
        }

        return "redirect:/family";
    }

    @PostMapping("/update")
    public String updateProfile(
            @Valid @ModelAttribute UpdateProfileRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("profileError", firstErrorMessage(bindingResult));
            return "redirect:/family";
        }

        try {
            profileService.updateProfile(currentUser.getId(), request);
            // Refresh security context so the updated display name appears in the navbar immediately
            securityContextHelper.refresh(currentUser.getEmail());
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("profileError", e.getMessage());
        }

        return "redirect:/family";
    }

    @PostMapping("/password")
    public String changePassword(
            @Valid @ModelAttribute ChangePasswordRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("passwordError", firstErrorMessage(bindingResult));
            return "redirect:/family";
        }

        try {
            profileService.changePassword(currentUser.getId(), request);
            redirectAttributes.addFlashAttribute("successMessage", "Password changed successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("passwordError", e.getMessage());
            return "redirect:/family";
        }

        return "redirect:/family";
    }

    // Extracts the first validation error message from a BindingResult.
    // Avoids repeating the same stream chain in every method that handles form errors.
    private String firstErrorMessage(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .findFirst()
                .orElse("Invalid input.");
    }
}
