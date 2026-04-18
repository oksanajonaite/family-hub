package com.familyhub.service;

import com.familyhub.dto.response.notification.NotificationResponse;
import com.familyhub.entity.Notification;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.NotificationType;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.NotificationNotFoundException;
import com.familyhub.mapper.NotificationMapper;
import com.familyhub.repository.NotificationRepository;
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
    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    // Returns the unread notification count — used for the navbar badge on every page load.
    // A dedicated COUNT query is intentional: fetching all notifications just for the number would be wasteful.
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    // Security check: a user may only mark their own notifications as read.
    // Attempting to mark another user's notification throws ForbiddenException.
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.getRecipient().getId().equals(userId)) {
            throw new ForbiddenException();
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    // Fetches only unread notifications — no need to touch already-read ones.
    // saveAll() performs a single DB round-trip instead of N separate save() calls.
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository
                .findAllByRecipientIdAndReadFalseOrderByCreatedAtDesc(userId);

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
