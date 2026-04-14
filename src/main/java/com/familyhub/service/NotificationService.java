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

    // readOnly = true — Hibernate skips dirty checking (no unnecessary UPDATE scans).
    // Returns a DTO list, not entities — the controller does not need to know about the DB structure.
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(CustomUserDetails currentUser) {
        return notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    // Returns the unread notification count — used for the navbar badge (e.g. "3 unread").
    // Spring Data JPA generates a COUNT query automatically from the method name.
    @Transactional(readOnly = true)
    public long countUnread(CustomUserDetails currentUser) {
        return notificationRepository.countByRecipientIdAndReadFalse(currentUser.getId());
    }

    // Security check: a user may only mark their own notifications as read.
    // Attempting to mark another user's notification throws AccessDeniedException.
    @Transactional
    public void markAsRead(Long notificationId, CustomUserDetails currentUser) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        if (!notification.getRecipient().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException();
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    // Fetches only unread notifications — no need to touch already-read ones.
    // saveAll() performs a single DB round-trip instead of N separate save() calls.
    @Transactional
    public void markAllAsRead(CustomUserDetails currentUser) {
        List<Notification> unread = notificationRepository
                .findAllByRecipientIdAndReadFalseOrderByCreatedAtDesc(currentUser.getId());

        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    // Creates a new notification. Called from other services:
    //   - TaskService: when a task is assigned to a user → TASK_ASSIGNED
    //   - (future) Scheduler: birthday reminders, health reminders, etc.
    //
    // relatedEntityType — type of the object that triggered the notification (e.g. "TASK", "EVENT")
    // relatedEntityId   — ID of that object (e.g. task.getId()) — used to build a link in the UI
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
                // New notifications are always unread by default
                .read(false)
                .build();

        return notificationRepository.save(notification);
    }
}
