package com.familyhub.dto.request.event;

import com.familyhub.entity.enums.RecurrenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreateEventRequest(

        @NotBlank(message = "Event title is required")
        @Size(max = 150, message = "Event title must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "Event description must be at most 2000 characters")
        String description,

        @NotNull(message = "Event start time is required")
        LocalDateTime startsAt,

        @NotNull(message = "Event end time is required")
        LocalDateTime endsAt,

        boolean privateEvent,

        @NotNull(message = "Recurrence type is required")
        RecurrenceType recurrenceType,

        LocalDate recurrenceUntil,

        List<Long> participantUserIds,

        List<Long> participantPetIds
) {}
