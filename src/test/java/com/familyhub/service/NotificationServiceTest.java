package com.familyhub.service;

import com.familyhub.entity.Notification;
import com.familyhub.entity.User;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.mapper.NotificationMapper;
import com.familyhub.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationService notificationService;

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: vartotojas gali žymėti kaip perskaitytą TIK savo pranešimus.
    // Be šio patikrinimo — vartotojas galėtų manipuliuoti kitų pranešimais
    // tiesiog žinodamas pranešimo ID.
    @Test
    void markAsRead_whenUserIsNotOwner_throwsForbiddenException() {
        // Arrange
        User owner = User.builder().id(1L).build();
        Long attackerId = 2L; // kitas vartotojas bando pažymėti ne savo pranešimą

        Notification notification = Notification.builder()
                .id(10L)
                .recipient(owner) // pranešimas priklauso vartotojui ID=1
                .read(false)
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        // Act & Assert
        // recipient.getId() (1) != attackerId (2) → ForbiddenException
        assertThrows(ForbiddenException.class,
                () -> notificationService.markAsRead(10L, attackerId));

        // Pranešimas NIEKADA neturėtų būti pakeistas
        verify(notificationRepository, never()).save(any());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Teigiamas testas: savininkas gali sėkmingai pažymėti pranešimą kaip perskaitytą.
    // Patvirtina kad apsauga neblokuoja teisėto vartotojo.
    @Test
    void markAsRead_whenUserIsOwner_marksNotificationAsRead() {
        // Arrange
        Long ownerId = 1L;
        User owner = User.builder().id(ownerId).build();

        Notification notification = Notification.builder()
                .id(10L)
                .recipient(owner)
                .read(false)
                .build();

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(notification);

        // Act
        notificationService.markAsRead(10L, ownerId);

        // Assert — pranešimas pažymėtas kaip perskaitytas ir išsaugotas
        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }
}
