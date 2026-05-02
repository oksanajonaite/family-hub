package com.familyhub.controller;

import com.familyhub.dto.request.pet.CreatePetRequest;
import com.familyhub.dto.request.pet.UpdatePetRequest;
import com.familyhub.entity.enums.Role;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.PetNotFoundException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.PetService;
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
 * CRUD for family pets, including photo upload. All write operations are restricted to PARENT role.
 * After any change, redirects back to /family where the pet list is displayed.
 */
@Controller
@RequestMapping("/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @GetMapping
    public String listPets() {
        return "redirect:/family";
    }

    @GetMapping("/create")
    public String createForm(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can add pets.");
            return "redirect:/family";
        }
        model.addAttribute("petRequest", new CreatePetRequest(null, null, null));
        return "pets/form";
    }

    @PostMapping("/create")
    public String createPet(
            @Valid @ModelAttribute("petRequest") CreatePetRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can add pets.");
            return "redirect:/family";
        }
        if (bindingResult.hasErrors()) {
            return "pets/form";
        }

        petService.createPet(request, currentUser.getFamilyId());
        redirectAttributes.addFlashAttribute("successMessage", "Pet added.");
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
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can edit pets.");
            return "redirect:/family";
        }

        try {
            UpdatePetRequest request = petService.toEditRequest(id, currentUser.getFamilyId());
            model.addAttribute("petRequest", request);
            model.addAttribute("petId", id);
            model.addAttribute("currentPet", petService.getPetById(id, currentUser.getFamilyId()));
            return "pets/form";
        } catch (PetNotFoundException | ForbiddenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Pet not found.");
            return "redirect:/family";
        }
    }

    @PostMapping("/{id}/edit")
    public String updatePet(
            @PathVariable Long id,
            @Valid @ModelAttribute("petRequest") UpdatePetRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can edit pets.");
            return "redirect:/family";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("petId", id);
            return "pets/form";
        }

        try {
            petService.updatePet(id, request, currentUser.getFamilyId());
            redirectAttributes.addFlashAttribute("successMessage", "Pet updated.");
        } catch (PetNotFoundException | ForbiddenException e) {
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
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can upload pet photos.");
            return "redirect:/family";
        }

        Optional<String> validationError = PhotoUploadValidator.validate(file);
        if (validationError.isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage", validationError.get());
            return "redirect:/pets/" + id + "/edit";
        }

        try {
            petService.uploadPhoto(id, currentUser.getFamilyId(), file);
            redirectAttributes.addFlashAttribute("successMessage", "Pet photo updated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Photo upload failed. Please try again.");
        }
        return "redirect:/pets/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String deletePet(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can remove pets.");
            return "redirect:/family";
        }

        try {
            petService.deletePet(id, currentUser.getFamilyId());
            redirectAttributes.addFlashAttribute("successMessage", "Pet removed.");
        } catch (PetNotFoundException | ForbiddenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/family";
    }
}
