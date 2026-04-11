package com.familyhub.dto.response.task;

import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(

        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Long assignedToUserId,
        String assignedToDisplayName,       // vartotojo vardas (jei priskirta user)
        Long assignedToMemberId,
        String assignedToMemberName,        // nario vardas (jei priskirta member)
        LocalDate dueDate,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}
