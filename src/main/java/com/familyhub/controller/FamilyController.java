package com.familyhub.controller;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.dto.request.family.JoinFamilyRequest;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.CannotRemoveMemberException;
import com.familyhub.exception.InvalidInviteCodeException;
import com.familyhub.exception.UserAlreadyInFamilyException;
import com.familyhub.exception.UserNotFoundException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.FamilyService;
import com.familyhub.util.SecurityContextHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Manages the family lifecycle: create, join via invite code, delete, and member removal.
 * Also handles invite code generation for PARENT and KID roles.
 * After create / join / delete the security context is refreshed so the session reflects the change immediately.
 */
@Controller
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;
    private final SecurityContextHelper securityContextHelper;

    @GetMapping
    public String familyPage(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        if (currentUser.getFamilyId() == null) {
            return "redirect:/family/setup";
        }
        model.addAttribute("page", familyService.buildFamilyPageData(currentUser.getFamilyId(), currentUser.getId()));
        return "family/index";
    }

    @GetMapping("/setup")
    public String setupPage(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        if (currentUser.getFamilyId() != null) {
            return "redirect:/family";
        }
        model.addAttribute("createFamilyRequest", new CreateFamilyRequest(""));
        model.addAttribute("joinFamilyRequest", new JoinFamilyRequest(""));
        return "family/setup";
    }

    @PostMapping("/create")
    public String createFamily(
            @Valid @ModelAttribute("createFamilyRequest") CreateFamilyRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("joinFamilyRequest", new JoinFamilyRequest(""));
            return "family/setup";
        }

        try {
            familyService.createFamily(request, currentUser.getId());
            securityContextHelper.refresh(currentUser.getEmail());
            redirectAttributes.addFlashAttribute("successMessage", "Family created successfully!");
            return "redirect:/family";
        } catch (UserAlreadyInFamilyException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/family";
        }
    }

    @PostMapping("/join")
    public String joinFamily(
            @Valid @ModelAttribute("joinFamilyRequest") JoinFamilyRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("createFamilyRequest", new CreateFamilyRequest(""));
            return "family/setup";
        }

        try {
            familyService.joinByInviteCode(request.inviteCode(), currentUser.getId());
            // Refresh the security context — the user's role may have changed (e.g. PARENT → KID)
            securityContextHelper.refresh(currentUser.getEmail());
            redirectAttributes.addFlashAttribute("successMessage", "You joined the family!");
            return "redirect:/family";
        } catch (InvalidInviteCodeException e) {
            bindingResult.rejectValue("inviteCode", "error.inviteCode", e.getMessage());
            model.addAttribute("createFamilyRequest", new CreateFamilyRequest(""));
            return "family/setup";
        } catch (UserAlreadyInFamilyException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/family";
        }
    }

    // @RequestParam Role role — form passes "role=PARENT" or "role=KID" to select which code to generate
    @PostMapping("/invite/generate")
    public String generateInviteCode(
            @RequestParam Role role,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        familyService.generateInviteCode(currentUser.getFamilyId(), currentUser.getId(), role);
        redirectAttributes.addFlashAttribute("successMessage", "New " + role + " invite code generated.");
        return "redirect:/family";
    }

    @PostMapping("/members/{id}/remove")
    public String removeMember(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can remove family members.");
            return "redirect:/family";
        }
        try {
            familyService.removeMember(id, currentUser.getId(), currentUser.getFamilyId());
            redirectAttributes.addFlashAttribute("successMessage", "Member removed from family.");
        } catch (UserNotFoundException | CannotRemoveMemberException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    @PostMapping("/delete")
    public String deleteFamily(
            @RequestParam String confirmedName,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can delete the family.");
            return "redirect:/family";
        }
        try {
            familyService.deleteFamily(currentUser.getFamilyId(), currentUser.getId(), confirmedName);
            // Clear the security context — user no longer belongs to a family, session must be refreshed
            securityContextHelper.refresh(currentUser.getEmail());
            redirectAttributes.addFlashAttribute("successMessage", "Your family has been deleted.");
            return "redirect:/family/setup";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/family";
        }
    }

}
