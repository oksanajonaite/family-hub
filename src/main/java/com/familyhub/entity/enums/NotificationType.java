package com.familyhub.entity.enums;

// Notification category — icon is a FontAwesome class used in templates via ${notification.type.icon}
public enum NotificationType {

    TASK_ASSIGNED       ("fa-solid fa-list-check"),
    TASK_COMPLETED      ("fa-solid fa-circle-check"),
    EVENT_REMINDER      ("fa-solid fa-calendar-day"),
    BIRTHDAY_REMINDER   ("fa-solid fa-cake-candles"),
    OVERDUE_TASK_REMINDER("fa-solid fa-triangle-exclamation"),
    SYSTEM              ("fa-solid fa-circle-info");

    private final String icon;

    NotificationType(String icon) {
        this.icon = icon;
    }

    // Getter used by Thymeleaf: ${notification.type.icon}
    public String getIcon() { return icon; }
}
