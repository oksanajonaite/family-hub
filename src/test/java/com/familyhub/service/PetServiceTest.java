package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.Pet;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.repository.EventParticipantRepository;
import com.familyhub.repository.FamilyRepository;
import com.familyhub.repository.PetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PetServiceTest {

    @Mock private PetRepository petRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private EventParticipantRepository eventParticipantRepository;
    @Mock private S3Service s3Service;

    @InjectMocks
    private PetService petService;

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: augintinis turi priklausyti tai pačiai šeimai.
    // Apsaugo nuo URL manipuliacijos — /pets/99/edit neturėtų veikti
    // jei augintinis priklauso kitai šeimai.
    @Test
    void getPetById_whenPetBelongsToDifferentFamily_throwsForbiddenException() {
        // Arrange
        Pet pet = Pet.builder()
                .id(1L)
                .name("Rex")
                .family(Family.builder().id(99L).build()) // priklauso KITAI šeimai
                .build();

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));

        // Act & Assert
        // Prašome augintinio ID=1, bet mūsų šeimos ID=10 — neatitinka → ForbiddenException
        assertThrows(ForbiddenException.class,
                () -> petService.getPetById(1L, 10L));
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Operacijų eiliškumas: S3 nuotrauka ištrinam PRIEŠ DB įrašą.
    // Jei DB ištrintume pirmiau — S3 key būtų prarastas ir failas
    // liktų "vaiduokliu" S3 krepšelyje (moka pinigus, niekada neišvalomas).
    @Test
    void deletePet_deletesS3PhotoBeforeDeletingFromDatabase() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        Pet pet = Pet.builder()
                .id(1L)
                .family(family)
                .photoUrl("pets/abc123.jpg") // turi nuotrauką S3
                .build();

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));

        // Act
        petService.deletePet(1L, 10L);

        // Assert — S3 turi būti išvalytas PRIEŠ DB ištrynimą
        InOrder inOrder = inOrder(s3Service, petRepository);
        inOrder.verify(s3Service).deleteFile("pets/abc123.jpg");
        inOrder.verify(petRepository).delete(pet);
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // FK constraint: event_participants.pet_id neleidžia ištrinti augintinio
    // jei jis yra priskirtas prie renginio kaip dalyvis.
    // Dalyviai turi būti ištrinti PRIEŠ trinant augintinį.
    @Test
    void deletePet_deletesEventParticipationsBeforeDeletingPet() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        Pet pet = Pet.builder()
                .id(1L)
                .family(family)
                .photoUrl(null)
                .build();

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));

        // Act
        petService.deletePet(1L, 10L);

        // Assert — dalyviai ištrinami prieš augintinį
        InOrder inOrder = inOrder(eventParticipantRepository, petRepository);
        inOrder.verify(eventParticipantRepository).deleteAllByPetId(1L);
        inOrder.verify(petRepository).delete(pet);
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Kai augintinio nėra nuotraukos — s3Service.deleteFile(null) vis tiek kviečiamas.
    // S3Service yra atsakingas už null saugų apdorojimą — PetService to netikrina.
    @Test
    void deletePet_whenPetHasNoPhoto_callsDeleteFileWithNull() {
        // Arrange
        Pet petWithoutPhoto = Pet.builder()
                .id(1L)
                .family(Family.builder().id(10L).build())
                .photoUrl(null) // nėra nuotraukos
                .build();

        when(petRepository.findById(1L)).thenReturn(Optional.of(petWithoutPhoto));

        // Act
        petService.deletePet(1L, 10L);

        // Assert
        verify(s3Service).deleteFile(null);
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // Nuotraukos keitimas: sena nuotrauka ištrinam PRIEŠ įkeliant naują.
    // Apsaugo nuo senų failų kaupimosi S3.
    @Test
    void uploadPhoto_whenPetAlreadyHasPhoto_deletesOldBeforeUploadingNew() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        Pet pet = Pet.builder()
                .id(1L)
                .family(family)
                .photoUrl("pets/old-photo.jpg") // sena nuotrauka
                .build();

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(s3Service.uploadFile(any(), eq("pets"))).thenReturn("pets/new-photo.jpg");

        MultipartFile file = mock(MultipartFile.class);

        // Act
        petService.uploadPhoto(1L, 10L, file);

        // Assert — sena ištrinam PRIEŠ įkeliant naują
        //InOrder naujas įrankis,tikrina metodų išvietimo eiliškumą.
        InOrder inOrder = inOrder(s3Service);
        inOrder.verify(s3Service).deleteFile("pets/old-photo.jpg");
        inOrder.verify(s3Service).uploadFile(file, "pets");
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────
    // S3 key (ne URL) išsaugomas DB.
    // PhotoController generuoja pre-signed URL iš key dinamiškai.
    @Test
    void uploadPhoto_savesS3KeyNotUrlToPet() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        Pet pet = Pet.builder()
                .id(1L)
                .family(family)
                .photoUrl(null)
                .build();

        when(petRepository.findById(1L)).thenReturn(Optional.of(pet));
        when(s3Service.uploadFile(any(), eq("pets"))).thenReturn("pets/uuid-photo.jpg");

        MultipartFile file = mock(MultipartFile.class);

        // Act
        petService.uploadPhoto(1L, 10L, file);

        // Assert — pet.photoUrl atnaujintas S3 key reikšme
        assertEquals("pets/uuid-photo.jpg", pet.getPhotoUrl());
        verify(petRepository).save(pet);
    }
}
