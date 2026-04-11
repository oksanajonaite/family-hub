package com.familyhub.dto.request.task;

import com.familyhub.entity.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateTaskRequest(

        @NotBlank(message = "Task title is required")
        @Size(max = 150, message = "Task title must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "Task description must be at most 2000 characters")
        String description,

        @NotNull(message = "Task priority is required")
        TaskPriority priority,

        Long assignedToUserId,      // vartotojas su paskyra (nullable)
        Long assignedToMemberId,    // šeimos narys be paskyros (nullable)

        LocalDate dueDate
) {}
