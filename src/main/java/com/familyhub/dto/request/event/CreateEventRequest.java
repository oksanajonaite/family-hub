package com.familyhub.dto.request.event;

import com.familyhub.entity.enums.RecurrenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateEventRequest(

        @NotBlank(message = "Event title is required")
        @Size(max = 150, message = "Event title must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "Event description must be at most 2000 characters")
        String description,

        // Date is required; time is optional — defaults to 00:00 in the service if null
        @NotNull(message = "Start date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @DateTimeFormat(pattern = "HH:mm")
        LocalTime startTime,

        // End date and time are fully optional
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        @DateTimeFormat(pattern = "HH:mm")
        LocalTime endTime,

        boolean privateEvent,

        @NotNull(message = "Recurrence type is required")
        RecurrenceType recurrenceType,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate recurrenceUntil,

        // Single list instead of three — prefix determines type: "USER_42", "PET_7", "MEMBER_15"
        List<String> participantIds
) {}
