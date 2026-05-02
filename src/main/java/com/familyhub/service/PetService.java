package com.familyhub.service;

import com.familyhub.dto.request.pet.CreatePetRequest;
import com.familyhub.dto.request.pet.UpdatePetRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.Pet;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.FamilyNotFoundException;
import com.familyhub.exception.PetNotFoundException;
import com.familyhub.mapper.PetMapper;
import com.familyhub.repository.EventParticipantRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;
    private final FamilyRepository familyRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final S3Service s3Service;
    private final PetMapper petMapper;

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

        Pet pet = petMapper.toEntity(request);
        pet.setFamily(family);

        return petRepository.save(pet);
    }

    @Transactional
    public Pet updatePet(Long petId, UpdatePetRequest request, Long familyId) {
        Pet pet = getPetById(petId, familyId);

        petMapper.updateEntity(request, pet);

        return petRepository.save(pet);
    }

    @Transactional
    public void deletePet(Long petId, Long familyId) {
        Pet pet = getPetById(petId, familyId);
        // Delete photo from S3 before removing the entity —
        // once the DB row is gone the key is lost and the file would become an orphan in S3
        s3Service.deleteFile(pet.getPhotoUrl());
        // Remove all event participations first — otherwise the FK constraint on event_participants.pet_id
        // would block the delete (pet cannot be deleted while it is referenced by another table)
        eventParticipantRepository.deleteAllByPetId(petId);
        petRepository.delete(pet);
    }

    // Returns the S3 key for the pet's photo, or null if no photo uploaded.
    // Used by PhotoController to generate a pre-signed URL for the response.
    // Family ownership is verified by getPetById — prevents accessing other families' photos.
    @Transactional(readOnly = true)
    public String getPhotoKey(Long petId, Long familyId) {
        return getPetById(petId, familyId).getPhotoUrl();
    }

    // Uploads a pet photo to S3, replaces the old one if it exists, persists the S3 key.
    // Folder "pets/" keeps pet photos separate from user avatars in the bucket.
    @Transactional
    public void uploadPhoto(Long petId, Long familyId, MultipartFile file) {
        Pet pet = getPetById(petId, familyId);
        s3Service.deleteFile(pet.getPhotoUrl());
        String key = s3Service.uploadFile(file, "pets");
        pet.setPhotoUrl(key);
        petRepository.save(pet);
    }
}
