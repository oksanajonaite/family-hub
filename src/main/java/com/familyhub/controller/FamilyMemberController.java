package com.familyhub.controller;

import com.familyhub.dto.request.member.CreateFamilyMemberRequest;
import com.familyhub.dto.request.member.UpdateFamilyMemberRequest;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.exception.FamilyMemberNotFoundException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.FamilyMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
            return "members/form";
        } catch (FamilyMemberNotFoundException | AccessDeniedException e) {
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
        } catch (FamilyMemberNotFoundException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
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
        } catch (FamilyMemberNotFoundException | AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }
}
