package com.familyhub.dto.request.task;

import com.familyhub.entity.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record UpdateTaskRequest(

        @NotBlank(message = "Task title is required")
        @Size(max = 150, message = "Task title must be at most 150 characters")
        String title,

        @Size(max = 2000, message = "Task description must be at most 2000 characters")
        String description,

        @NotNull(message = "Task priority is required")
        TaskPriority priority,

        // Multiple assignees — prefix determines type: "USER_42" or "MEMBER_15"
        List<String> assigneeIds,

        LocalDate dueDate,

        // When true, only the creator and PARENT can see this task
        boolean privateTask
) {}
