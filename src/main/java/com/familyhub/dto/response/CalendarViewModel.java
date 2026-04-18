package com.familyhub.dto.response;

import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.TaskItem;

import java.time.LocalDate;
import java.util.List;

// View model for the calendar dashboard — bundles all data needed by dashboard.html.
// Avoids 6 separate model.addAttribute() calls in DashboardController.
public record CalendarViewModel(
        List<List<CalendarDay>> weeks,
        String monthLabel,
        LocalDate prevMonth,
        LocalDate nextMonth,
        LocalDate selectedDate,
        List<EventResponse> upcomingEvents,
        List<TaskItem> dueSoonTasks,
        long todayEventsCount,
        long attentionTasksCount,
        long completedTasksCount,
        long totalTasksCount
) {}
