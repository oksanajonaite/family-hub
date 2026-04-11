package com.familyhub.controller;

import com.familyhub.dto.request.family.CreateFamilyRequest;
import com.familyhub.dto.request.family.JoinFamilyRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.exception.InvalidInviteCodeException;
import com.familyhub.exception.UserAlreadyInFamilyException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.security.CustomUserDetailsService;
import com.familyhub.service.FamilyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {

    // Controller turi tik du priklausomumus:
    // 1. Service (business logika)
    // 2. UserDetailsService (session refresh — infrastructure logika)
    // Repository čia NETURI būti — tai service'o atsakomybė
    private final FamilyService familyService;
    private final CustomUserDetailsService userDetailsService;

    @GetMapping
    public String familyPage(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        if (currentUser.getFamilyId() == null) {
            return "redirect:/family/setup";
        }
        model.addAttribute("family", familyService.getFamily(currentUser.getFamilyId()));
        model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
        model.addAttribute("inviteCode", familyService.getActiveInviteCode(currentUser.getFamilyId()));
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
            // getUserById() — service'as kreipiasi į repository, ne controller
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

    @PostMapping("/invite/generate")
    public String generateInviteCode(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        User user = familyService.getUserById(currentUser.getId());
        familyService.generateInviteCode(user.getFamily(), user);
        redirectAttributes.addFlashAttribute("successMessage", "New invite code generated.");
        return "redirect:/family";
    }

    private void refreshSecurityContext(String email) {
        UserDetails fresh = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
