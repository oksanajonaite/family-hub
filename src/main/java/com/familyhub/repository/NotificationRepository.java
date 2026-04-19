package com.familyhub.repository;

import com.familyhub.entity.Notification;
import com.familyhub.entity.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByRecipientIdOrderByCreatedAtDesc(Long recipientUserId);

    List<Notification> findAllByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientUserId); // unread only

    long countByRecipientIdAndReadFalse(Long recipientUserId); // used for the navbar badge count

    // Used when deleting an entire family — removes all notifications for all family members at once
    void deleteAllByRecipientIdIn(List<Long> userIds);

    // Used by the cleanup scheduler — removes notifications older than 7 days
    void deleteAllByCreatedAtBefore(LocalDateTime cutoff);

    // Used by the event reminder scheduler — prevents sending a duplicate reminder for the same event.
    boolean existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityId(
            Long recipientId, NotificationType type, String relatedEntityType, Long relatedEntityId
    );

    // Used by the birthday reminder scheduler — checks if a reminder was already sent today.
    // "since" is set to midnight of today so the guard resets every day (same birthday, next year → new notification).
    boolean existsByRecipientIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
            Long recipientId, NotificationType type, String relatedEntityType, Long relatedEntityId,
            LocalDateTime since
    );
}
