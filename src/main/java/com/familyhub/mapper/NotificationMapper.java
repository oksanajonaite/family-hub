package com.familyhub.mapper;

import com.familyhub.dto.response.notification.NotificationResponse;
import com.familyhub.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
