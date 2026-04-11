package com.familyhub.controller;

import com.familyhub.dto.request.pet.CreatePetRequest;
import com.familyhub.dto.request.pet.UpdatePetRequest;
import com.familyhub.entity.Pet;
import com.familyhub.entity.enums.PetType;
import com.familyhub.entity.enums.Role;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.PetService;
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
@RequestMapping("/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @GetMapping
    public String listPets(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        model.addAttribute("pets", petService.getFamilyPets(currentUser.getFamilyId()));
        model.addAttribute("isParent", currentUser.getRole() == Role.PARENT);
        return "pets/index";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("petRequest", new CreatePetRequest(null, PetType.OTHER, null));
        model.addAttribute("petTypes", PetType.values());
        return "pets/form";
    }

    @PostMapping("/create")
    public String createPet(
            @Valid @ModelAttribute("petRequest") CreatePetRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("petTypes", PetType.values());
            return "pets/form";
        }

        petService.createPet(request, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Pet added.");
        return "redirect:/pets";
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        Pet pet = petService.getPetById(id, currentUser.getFamilyId());

        UpdatePetRequest request = new UpdatePetRequest(
                pet.getName(), pet.getType(), pet.getDateOfBirth()
        );

        model.addAttribute("petRequest", request);
        model.addAttribute("petId", id);
        model.addAttribute("petTypes", PetType.values());
        return "pets/form";
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
        if (bindingResult.hasErrors()) {
            model.addAttribute("petId", id);
            model.addAttribute("petTypes", PetType.values());
            return "pets/form";
        }

        petService.updatePet(id, request, currentUser.getFamilyId());
        redirectAttributes.addFlashAttribute("successMessage", "Pet updated.");
        return "redirect:/pets";
    }

    @PostMapping("/{id}/delete")
    public String deletePet(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        petService.deletePet(id, currentUser.getFamilyId());
        redirectAttributes.addFlashAttribute("successMessage", "Pet removed.");
        return "redirect:/pets";
    }
}
