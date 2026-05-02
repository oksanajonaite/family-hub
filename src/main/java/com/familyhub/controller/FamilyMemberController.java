package com.familyhub.controller;

import com.familyhub.dto.request.member.CreateFamilyMemberRequest;
import com.familyhub.dto.request.member.UpdateFamilyMemberRequest;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.FamilyMemberNotFoundException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.FamilyMemberService;
import com.familyhub.util.PhotoUploadValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Manages non-account family members (e.g. young children who do not have a login).
 * All write operations are restricted to PARENT role.
 * After any change, redirects back to /family where the member list is displayed.
 */
@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class FamilyMemberController {

    private final FamilyMemberService familyMemberService;

    @GetMapping
    public String listMembers() {
        return "redirect:/family";
    }

    @GetMapping("/create")
    public String createForm(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can add family members.");
            return "redirect:/family";
        }
        model.addAttribute("memberRequest", new CreateFamilyMemberRequest(null, null));
        return "members/form";
    }

    @PostMapping("/create")
    public String createMember(
            @Valid @ModelAttribute("memberRequest") CreateFamilyMemberRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can add family members.");
            return "redirect:/family";
        }
        if (bindingResult.hasErrors()) {
            return "members/form";
        }

        familyMemberService.createMember(request, currentUser.getFamilyId());
        redirectAttributes.addFlashAttribute("successMessage", "Member added.");
        return "redirect:/family";
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can edit family members.");
            return "redirect:/family";
        }

        try {
            UpdateFamilyMemberRequest request = familyMemberService.toEditRequest(id, currentUser.getFamilyId());
            model.addAttribute("memberRequest", request);
            model.addAttribute("memberId", id);
            model.addAttribute("currentMember", familyMemberService.getMemberById(id, currentUser.getFamilyId()));
            return "members/form";
        } catch (FamilyMemberNotFoundException | ForbiddenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Member not found.");
            return "redirect:/family";
        }
    }

    @PostMapping("/{id}/edit")
    public String updateMember(
            @PathVariable Long id,
            @Valid @ModelAttribute("memberRequest") UpdateFamilyMemberRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can edit family members.");
            return "redirect:/family";
        }
        if (bindingResult.hasErrors()) {
            return "members/form";
        }

        try {
            familyMemberService.updateMember(id, request, currentUser.getFamilyId());
            redirectAttributes.addFlashAttribute("successMessage", "Member updated.");
        } catch (FamilyMemberNotFoundException | ForbiddenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping(value = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can upload member photos.");
            return "redirect:/family";
        }

        Optional<String> validationError = PhotoUploadValidator.validate(file);
        if (validationError.isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", validationError.get());
            return "redirect:/members/" + id + "/edit";
        }

        try {
            familyMemberService.uploadPhoto(id, currentUser.getFamilyId(), file);
            redirectAttributes.addFlashAttribute("successMessage", "Photo updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Photo upload failed. Please try again.");
        }
        return "redirect:/members/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String deleteMember(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can remove family members.");
            return "redirect:/family";
        }

        try {
            familyMemberService.deleteMember(id, currentUser.getFamilyId());
            redirectAttributes.addFlashAttribute("successMessage", "Member removed.");
        } catch (FamilyMemberNotFoundException | ForbiddenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }
}
