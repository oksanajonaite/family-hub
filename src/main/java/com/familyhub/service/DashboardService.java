package com.familyhub.service;

import com.familyhub.dto.response.CalendarDay;
import com.familyhub.dto.response.CalendarViewModel;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EventService eventService;
    private final TaskService taskService;

    public CalendarViewModel buildCalendarViewModel(
            Integer year,
            Integer month,
            LocalDate selected,
            CustomUserDetails currentUser
    ) {
        Long familyId = currentUser.getFamilyId();
        LocalDate today = LocalDate.now();

        LocalDate viewDate = (year != null && month != null)
                ? LocalDate.of(year, month, 1)
                : today.withDayOfMonth(1);
        LocalDate selectedDate = resolveSelectedDate(selected, viewDate, today);

        LocalDate calStart = viewDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate calEnd = viewDate.with(TemporalAdjusters.lastDayOfMonth())
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<EventResponse> calendarEvents = eventService.getVisibleFamilyEventsBetween(
                familyId, calStart.atStartOfDay(), calEnd.atTime(23, 59, 59), currentUser);

        List<TaskItem> calendarTasks = taskService.getFamilyTasksBetween(familyId, calStart, calEnd)
                .stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE)
                .toList();

        // Format month name in English — JVM default locale (Lithuanian) would produce Lithuanian names
        String monthLabel = viewDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));

        // 1 DB query for sidebar events (today + tomorrow), then split in memory
        List<EventResponse> sidebarEvents = eventService.getVisibleFamilyEventsBetween(
                familyId, today.atStartOfDay(), today.plusDays(1).atTime(23, 59, 59), currentUser);
        List<EventResponse> upcomingEvents = sidebarEvents.stream().limit(4).toList();
        long todayEventsCount = sidebarEvents.stream()
                .filter(e -> !e.startsAt().toLocalDate().isAfter(today))
                .count();

        // 1 DB query for all tasks, then split in memory
        List<TaskItem> allTasks = taskService.getFamilyTasks(familyId);
        List<TaskItem> todoTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).toList();
        List<TaskItem> doneTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).toList();
        List<TaskItem> dueSoonTasks = allTasks.stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE)
                .filter(task -> task.getDueDate() != null)
                .filter(task -> !task.getDueDate().isBefore(today) && !task.getDueDate().isAfter(today.plusDays(1)))
                .limit(4)
                .toList();
        long attentionTasksCount = todoTasks.stream()
                .filter(task -> task.getPriority() == TaskPriority.HIGH
                        || (task.getDueDate() != null && !task.getDueDate().isAfter(today.plusDays(2))))
                .count();

        return new CalendarViewModel(
                buildWeeks(calStart, calEnd, viewDate, calendarEvents, calendarTasks, today, selectedDate),
                monthLabel,
                viewDate.minusMonths(1),
                viewDate.plusMonths(1),
                selectedDate,
                upcomingEvents,
                dueSoonTasks,
                todayEventsCount,
                attentionTasksCount,
                doneTasks.size(),
                allTasks.size()
        );
    }

    // If a date was clicked, use it. Otherwise default to today when viewing the current month,
    // or the 1st of the month when browsing past/future months.
    private LocalDate resolveSelectedDate(LocalDate selected, LocalDate viewDate, LocalDate today) {
        if (selected != null) return selected;
        boolean isCurrentMonth = viewDate.getYear() == today.getYear()
                && viewDate.getMonth() == today.getMonth();
        return isCurrentMonth ? today : viewDate;
    }

    // Builds the calendar grid as a list of weeks, each containing exactly 7 CalendarDay entries
    private List<List<CalendarDay>> buildWeeks(
            LocalDate calStart,
            LocalDate calEnd,
            LocalDate viewDate,
            List<EventResponse> events,
            List<TaskItem> tasks,
            LocalDate today,
            LocalDate selectedDate
    ) {
        List<List<CalendarDay>> weeks = new ArrayList<>();
        LocalDate current = calStart;

        while (!current.isAfter(calEnd)) {
            List<CalendarDay> week = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate day = current;
                List<EventResponse> dayEvents = events.stream()
                        .filter(e -> e.startsAt().toLocalDate().equals(day))
                        .toList();
                List<TaskItem> dayTasks = tasks.stream()
                        .filter(t -> day.equals(t.getDueDate()))
                        .toList();
                week.add(new CalendarDay(
                        day,
                        dayEvents,
                        dayTasks,
                        day.getMonth() == viewDate.getMonth(),
                        day.equals(today),
                        day.equals(selectedDate)
                ));
                current = current.plusDays(1);
            }
            weeks.add(week);
        }

        return weeks;
    }
}
