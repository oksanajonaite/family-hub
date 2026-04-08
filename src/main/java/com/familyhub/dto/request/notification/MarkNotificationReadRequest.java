package com.familyhub.dto.request.notification;

import jakarta.validation.constraints.NotNull;

public record MarkNotificationReadRequest(

        @NotNull(message = "Read flag is required")
        Boolean read
) {}
