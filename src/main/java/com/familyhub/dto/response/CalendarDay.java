package com.familyhub.dto.response;

import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.TaskItem;

import java.time.LocalDate;
import java.util.List;

// Holds data for a single calendar day.
// currentMonth = false means the day belongs to the previous or next month (rendered dimmed)
public record CalendarDay(
        LocalDate date,
        List<EventResponse> events,
        List<TaskItem> tasks,
        boolean currentMonth,
        boolean today
) {}
