package com.familyhub.service;

import com.familyhub.dto.request.pet.CreatePetRequest;
import com.familyhub.dto.request.pet.UpdatePetRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.Pet;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.PetNotFoundException;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.PetRepository;
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
                .orElseThrow(() -> new PetNotFoundException(petId));

        // Guard against URL manipulation — the pet must belong to this family
        if (!pet.getFamily().getId().equals(familyId)) {
            throw new ForbiddenException();
        }
        return pet;
    }

    @Transactional(readOnly = true)
    public UpdatePetRequest toEditRequest(Long petId, Long familyId) {
        Pet pet = getPetById(petId, familyId);
        return new UpdatePetRequest(pet.getName(), pet.getType(), pet.getDateOfBirth());
    }

    @Transactional
    public Pet createPet(CreatePetRequest request, Long familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new FamilyNotFoundException(familyId));

        Pet pet = Pet.builder()
                .family(family)
                .name(request.name())
                .type(request.type())
                .dateOfBirth(request.dateOfBirth())
                .build();

        return petRepository.save(pet);
    }

    @Transactional
    public Pet updatePet(Long petId, UpdatePetRequest request, Long familyId) {
        Pet pet = getPetById(petId, familyId);

        pet.setName(request.name());
        pet.setType(request.type());
        pet.setDateOfBirth(request.dateOfBirth());

        return petRepository.save(pet);
    }

    @Transactional
    public void deletePet(Long petId, Long familyId) {
        Pet pet = getPetById(petId, familyId);
        petRepository.delete(pet);
    }
}
