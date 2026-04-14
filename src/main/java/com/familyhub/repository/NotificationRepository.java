package com.familyhub.repository;

import com.familyhub.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByRecipientIdOrderByCreatedAtDesc(Long recipientUserId);
    List<Notification> findAllByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientUserId); // unread only
    long countByRecipientIdAndReadFalse(Long recipientUserId); // used for the navbar badge count
}
