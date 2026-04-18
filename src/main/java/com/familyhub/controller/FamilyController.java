package com.familyhub.controller;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.dto.request.family.JoinFamilyRequest;
import com.familyhub.dto.response.FamilyPageData;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.CannotRemoveMemberException;
import com.familyhub.exception.InvalidInviteCodeException;
import com.familyhub.exception.UserAlreadyInFamilyException;
import com.familyhub.exception.UserNotFoundException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.FamilyMemberService;
import com.familyhub.service.FamilyService;
import com.familyhub.service.PetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;
    private final FamilyMemberService familyMemberService;
    private final PetService petService;
    private final UserDetailsService userDetailsService;

    @GetMapping
    public String familyPage(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        if (currentUser.getFamilyId() == null) {
            return "redirect:/family/setup";
        }
        final Long familyId = currentUser.getFamilyId();
        model.addAttribute("page", new FamilyPageData(
                familyService.getFamily(familyId),
                familyService.getFamilyUsers(familyId),
                familyMemberService.getFamilyMembers(familyId),
                petService.getFamilyPets(familyId),
                familyService.getActiveInviteCode(familyId, Role.PARENT),
                familyService.getActiveInviteCode(familyId, Role.KID),
                currentUser.getId()
        ));
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
            User user = familyService.getUserById(currentUser.getId());
            familyService.createFamily(request, user);
            refreshSecurityContext(currentUser.getEmail());
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
            User user = familyService.getUserById(currentUser.getId());
            familyService.joinByInviteCode(request.inviteCode(), user);
            // Refresh the security context — the user's role may have changed (e.g. PARENT → KID)
            refreshSecurityContext(currentUser.getEmail());
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
        User user = familyService.getUserById(currentUser.getId());
        familyService.generateInviteCode(user.getFamily(), user, role);
        redirectAttributes.addFlashAttribute("successMessage", "New " + role + " invite code generated.");
        return "redirect:/family";
    }

    @PostMapping("/members/{id}/remove")
    public String removeMember(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User parent = familyService.getUserById(currentUser.getId());
            familyService.removeMember(id, parent);
            redirectAttributes.addFlashAttribute("successMessage", "Member removed from family.");
        } catch (UserNotFoundException | CannotRemoveMemberException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }

    // Reload user from DB and replace the current authentication in the security context.
    // Needed after join/create family so that familyId and role are immediately up to date
    // without requiring a new login.
    private void refreshSecurityContext(String email) {
        UserDetails fresh = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
