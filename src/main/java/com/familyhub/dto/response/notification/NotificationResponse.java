package com.familyhub.dto.response.notification;

import com.familyhub.entity.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(

        Long id,
        NotificationType type,
        String message,
        boolean read,
        String relatedEntityType,
        Long relatedEntityId,
        LocalDateTime createdAt
) {}
