package com.familyhub.service;

import com.familyhub.dto.request.pet.CreatePetRequest;
import com.familyhub.dto.request.pet.UpdatePetRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.Pet;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.PetRepository;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final FamilyRepository familyRepository;

    @Transactional(readOnly = true)
    public List<Pet> getFamilyPets(Long familyId) {
        return petRepository.findAllByFamilyId(familyId);
    }

    @Transactional(readOnly = true)
    public Pet getPetById(Long petId, Long familyId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new RuntimeException("Pet not found: " + petId));

        // Apsauga nuo URL manipuliavimo — gyvūnas turi priklausyti šiai šeimai
        if (!pet.getFamily().getId().equals(familyId)) {
            throw new AccessDeniedException();
        }
        return pet;
    }

    // Tik PARENT gali pridėti gyvūną — tikrinama controller'yje
    @Transactional
    public Pet createPet(CreatePetRequest request, CustomUserDetails currentUser) {
        Family family = familyRepository.findById(currentUser.getFamilyId())
                .orElseThrow(() -> new IllegalStateException("Family not found"));

        Pet pet = Pet.builder()
                .family(family)
                .name(request.name())
                .type(request.type())
                .dateOfBirth(request.dateOfBirth())
                .build();

        return petRepository.save(pet);
    }

    // Tik PARENT gali redaguoti — tikrinama controller'yje
    @Transactional
    public Pet updatePet(Long petId, UpdatePetRequest request, Long familyId) {
        Pet pet = getPetById(petId, familyId);

        pet.setName(request.name());
        pet.setType(request.type());
        pet.setDateOfBirth(request.dateOfBirth());

        return petRepository.save(pet);
    }

    // Tik PARENT gali trinti — tikrinama controller'yje
    @Transactional
    public void deletePet(Long petId, Long familyId) {
        Pet pet = getPetById(petId, familyId);
        petRepository.delete(pet);
    }
}
