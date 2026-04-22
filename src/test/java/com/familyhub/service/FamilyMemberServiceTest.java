package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.FamilyMember;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.repository.EventParticipantRepository;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.FamilyRepository;
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
class FamilyMemberServiceTest {

    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private FamilyRepository familyRepository;
    @Mock private EventParticipantRepository eventParticipantRepository;
    @Mock private S3Service s3Service;

    @InjectMocks
    private FamilyMemberService familyMemberService;

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: narys turi priklausyti tai pačiai šeimai.
    // Apsaugo nuo URL manipuliacijos — vartotojas negali pasiekti kitos šeimos narių
    // tiesiog pakeisdamas ID URL juostoje (pvz. /members/99/edit).
    @Test
    void getMemberById_whenMemberBelongsToDifferentFamily_throwsForbiddenException() {
        // Arrange
        FamilyMember member = FamilyMember.builder()
                .id(1L)
                .name("Grandma")
                .family(Family.builder().id(99L).build()) // priklauso KITAI šeimai
                .build();

        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        // Act & Assert
        // Prašome nario ID=1, bet mūsų šeimos ID=10 — neatitinka → ForbiddenException
        assertThrows(ForbiddenException.class,
                () -> familyMemberService.getMemberById(1L, 10L));
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Operacijų eiliškumas: S3 nuotrauka turi būti ištrinta PRIEŠ DB įrašą.
    // Jei ištrintume DB įrašą pirmiau — S3 key būtų prarastas ir failas
    // liktų "vaiduokliu" S3 krepšelyje (niekada nebus išvalytas, moka pinigus).
    //
    // InOrder — Mockito įrankis tikrinti kad metodai kviečiami nurodyta tvarka.
    // Alternatyva: verify() neparodo eiliškumo — tik ar metodas buvo iškviestas.
    @Test
    void deleteMember_deletesS3PhotoBeforeDeletingFromDatabase() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        FamilyMember member = FamilyMember.builder()
                .id(1L)
                .family(family)
                .photoUrl("members/abc123.jpg") // turi nuotrauką S3
                .build();

        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        // Act
        familyMemberService.deleteMember(1L, 10L);

        // Assert — S3 turi būti išvalytas PRIEŠ DB ištrynimą
        InOrder inOrder = inOrder(s3Service, familyMemberRepository);
        inOrder.verify(s3Service).deleteFile("members/abc123.jpg");
        inOrder.verify(familyMemberRepository).delete(member);
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // FK constraint: event_participants.family_member_id neleidžia ištrinti nario
    // jei jis yra priskirtas prie renginio kaip dalyvis.
    // Dalyviai turi būti ištrinti PRIEŠ trinant narį.
    @Test
    void deleteMember_deletesEventParticipationsBeforeDeletingMember() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        FamilyMember member = FamilyMember.builder()
                .id(1L)
                .family(family)
                .photoUrl(null)
                .build();

        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        // Act
        familyMemberService.deleteMember(1L, 10L);

        // Assert — dalyviai ištrinami prieš narį
        InOrder inOrder = inOrder(eventParticipantRepository, familyMemberRepository);
        inOrder.verify(eventParticipantRepository).deleteAllByFamilyMemberId(1L);
        inOrder.verify(familyMemberRepository).delete(member);
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Kai nario nėra nuotraukos — s3Service.deleteFile(null) vis tiek kviečiamas.
    // Tai S3Service atsakomybė saugiai apdoroti null — FamilyMemberService
    // neturėtų tikrinti ar nuotrauka egzistuoja prieš kviesdamas deleteFile().
    @Test
    void deleteMember_whenMemberHasNoPhoto_callsDeleteFileWithNull() {
        // Arrange
        FamilyMember memberWithoutPhoto = FamilyMember.builder()
                .id(1L)
                .family(Family.builder().id(10L).build())
                .photoUrl(null) // nėra nuotraukos
                .build();

        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(memberWithoutPhoto));

        // Act
        familyMemberService.deleteMember(1L, 10L);

        // Assert — deleteFile(null) kviečiamas — S3Service apdoroja null viduje
        verify(s3Service).deleteFile(null);
    }

    // ── Test 5 ────────────────────────────────────────────────────────────────
    // Nuotraukos keitimas: sena nuotrauka turi būti ištrinta prieš įkeliant naują.
    // Apsaugo nuo "vaiduoklių" — senų failų, kurie niekada neišvalomi S3.
    @Test
    void uploadPhoto_whenMemberAlreadyHasPhoto_deletesOldBeforeUploadingNew() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        FamilyMember member = FamilyMember.builder()
                .id(1L)
                .family(family)
                .photoUrl("members/old-photo.jpg") // sena nuotrauka
                .build();

        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(s3Service.uploadFile(any(), eq("members"))).thenReturn("members/new-photo.jpg");

        MultipartFile file = mock(MultipartFile.class);

        // Act
        familyMemberService.uploadPhoto(1L, 10L, file);

        // Assert — sena ištrinam PRIEŠ įkeliant naują
        InOrder inOrder = inOrder(s3Service);
        inOrder.verify(s3Service).deleteFile("members/old-photo.jpg");
        inOrder.verify(s3Service).uploadFile(file, "members");
    }

    // ── Test 6 ────────────────────────────────────────────────────────────────
    // S3 key (ne URL) išsaugomas DB.
    // Pvz. "members/abc123.jpg" — ne "https://bucket.s3.amazonaws.com/members/abc123.jpg".
    // PhotoController generuoja pre-signed URL iš key dinamiškai — key turi būti trumpas.
    @Test
    void uploadPhoto_savesS3KeyNotUrlToMember() {
        // Arrange
        Family family = Family.builder().id(10L).build();
        FamilyMember member = FamilyMember.builder()
                .id(1L)
                .family(family)
                .photoUrl(null)
                .build();

        when(familyMemberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(s3Service.uploadFile(any(), eq("members"))).thenReturn("members/uuid-photo.jpg");

        MultipartFile file = mock(MultipartFile.class);

        // Act
        familyMemberService.uploadPhoto(1L, 10L, file);

        // Assert — member.photoUrl atnaujintas S3 key reikšme
        assertEquals("members/uuid-photo.jpg", member.getPhotoUrl());
        verify(familyMemberRepository).save(member);
    }
}
