package com.familyhub.service;

import com.familyhub.dto.response.notification.NotificationResponse;
import com.familyhub.entity.Notification;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.NotificationType;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.mapper.NotificationMapper;
import com.familyhub.repository.NotificationRepository;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    // --- Vartotojo pranešimų gavimas ---
    // readOnly = true — Hibernate neatlieks "dirty checking" (nereikalingo UPDATE tikrinimo).
    // Grąžiname DTO sąrašą, ne entity — controller'iui nereikia žinoti apie DB struktūrą.
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(CustomUserDetails currentUser) {
        return notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                // Kiekvienas Notification entity → NotificationResponse DTO
                .map(notificationMapper::toResponse)
                .toList();
    }

    // --- Neperskaitytų pranešimų skaičius ---
    // Naudojamas naršyklės badge'ui (pvz. "🔔 3").
    // countBy... — Spring Data JPA generuoja COUNT SQL užklausą automatiškai.
    @Transactional(readOnly = true)
    public long countUnread(CustomUserDetails currentUser) {
        return notificationRepository.countByRecipientIdAndReadFalse(currentUser.getId());
    }

    // --- Vieno pranešimo pažymėjimas kaip perskaitytas ---
    // Saugumo patikrinimas: vartotojas gali žymėti TIK savo pranešimus.
    // Jei bandytų pažymėti kito vartotojo pranešimą — metame AccessDeniedException.
    @Transactional
    public void markAsRead(Long notificationId, CustomUserDetails currentUser) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        // Tikriname ar pranešimas priklauso dabartiniam vartotojui
        if (!notification.getRecipient().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException();
        }

        notification.setRead(true);
        // save() — Hibernate aptiks pakeitimą ir atliks UPDATE į DB
        notificationRepository.save(notification);
    }

    // --- Visų pranešimų pažymėjimas kaip perskaityti ---
    // Gauna tik neperskaitytus — nereikia liesti jau perskaitytų (optimizacija).
    // findAllByRecipientIdAndReadFalse — Spring Data JPA automatiškai generuoja:
    // SELECT * FROM notifications WHERE recipient_user_id = ? AND is_read = false
    @Transactional
    public void markAllAsRead(CustomUserDetails currentUser) {
        List<Notification> unread = notificationRepository
                .findAllByRecipientIdAndReadFalseOrderByCreatedAtDesc(currentUser.getId());

        // Kiekvienam neperskaitytam — pažymime kaip perskaitytą
        unread.forEach(n -> n.setRead(true));

        // saveAll() — vienas DB roundtrip vietoje N atskirų save() kvietimų
        notificationRepository.saveAll(unread);
    }

    // --- Naujo pranešimo sukūrimas ---
    // Šis metodas bus kviečiamas iš kitų service'ų:
    //   - TaskService: kai užduotis priskiriama vartotojui → TASK_ASSIGNED
    //   - (ateityje) Scheduler: gimtadienių priminimai, sveikatos priminiai ir kt.
    //
    // relatedEntityType — kokio tipo objektas sukėlė pranešimą (pvz. "TASK", "EVENT")
    // relatedEntityId   — to objekto ID (pvz. task.getId()) — naudojama nuorodai sukurti UI
    @Transactional
    public Notification createNotification(
            User recipient,
            NotificationType type,
            String message,
            String relatedEntityType,
            Long relatedEntityId
    ) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                // read = false — numatytoji reikšmė, naujas pranešimas visada neperskaitytas
                .read(false)
                .build();

        return notificationRepository.save(notification);
    }
}
