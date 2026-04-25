package com.familyhub.dto.response.calendar;

import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.dto.response.holiday.HolidayEntry;
import com.familyhub.entity.TaskItem;

import java.time.LocalDate;
import java.util.List;

// View model for the calendar dashboard — bundles all data needed by dashboard.html.
// Avoids many separate model.addAttribute() calls in DashboardController.
public record CalendarViewModel(
        List<List<CalendarDay>> weeks,
        List<CalendarDay> mobileDays,
        CalendarDay selectedDay,
        LocalDate mobilePrevDate,
        LocalDate mobileNextDate,
        String monthLabel,
        LocalDate prevMonth,
        LocalDate nextMonth,
        LocalDate selectedDate,
        List<EventResponse> upcomingEvents,
        List<BirthdayEntry> upcomingBirthdays,
        List<HolidayEntry> upcomingHolidays,
        List<TaskItem> dueSoonTasks,
        long todayEventsCount,
        long attentionTasksCount,
        long completedTasksCount,
        long totalTasksCount
) {}
