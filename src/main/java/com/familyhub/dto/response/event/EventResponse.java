package com.familyhub.dto.response.event;

import com.familyhub.entity.enums.RecurrenceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EventResponse(

        Long id,
        String title,
        String description,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean privateEvent,
        RecurrenceType recurrenceType,
        LocalDate recurrenceUntil,
        Long createdByUserId,
        List<Long> participantUserIds,
        List<Long> participantPetIds
) {}
