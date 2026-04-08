package com.familyhub.dto.request.task;

import com.familyhub.entity.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(

        @NotNull(message = "Task status is required")
        TaskStatus status
) {}
