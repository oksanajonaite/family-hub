package com.familyhub.dto.response.task;

import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TaskResponse(

        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        List<String> assigneeIds,    // prefiksuoti IDs formai: "USER_42", "MEMBER_15"
        List<String> assigneeNames,  // rodymui: ["Jonas", "Mama"]
        LocalDate dueDate,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}
