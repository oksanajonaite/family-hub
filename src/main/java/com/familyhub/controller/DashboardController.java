package com.familyhub.controller;

import com.familyhub.dto.response.CalendarDay;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.EventService;
import com.familyhub.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final EventService eventService;
    private final TaskService taskService;

    @GetMapping("/dashboard")
    public String dashboard(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model
    ) {
        // ADMIN has no family — redirect straight to the admin panel
        if (currentUser.getRole() == Role.ADMIN) {
            return "redirect:/admin";
        }

        LocalDate today = LocalDate.now();

        // Default to the current month if no year/month query params are provided
        LocalDate viewDate = (year != null && month != null)
                ? LocalDate.of(year, month, 1)
                : today.withDayOfMonth(1);

        // Calendar grid starts on the Monday of the week containing the 1st of the month,
        // and ends on the Sunday of the week containing the last day of the month
        LocalDate calStart = viewDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate calEnd = viewDate.with(TemporalAdjusters.lastDayOfMonth())
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<EventResponse> events = eventService.getVisibleFamilyEventsBetween(
                currentUser.getFamilyId(),
                calStart.atStartOfDay(),
                calEnd.atTime(23, 59, 59),
                currentUser
        );

        List<TaskItem> tasks = taskService.getFamilyTasksBetween(
                currentUser.getFamilyId(), calStart, calEnd
        );

        // Format month name here with Locale.ENGLISH instead of using #temporals.format in Thymeleaf.
        // Thymeleaf would use the JVM default locale (Lithuanian), producing Lithuanian month names.
        String monthLabel = viewDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));

        // Upcoming events for sidebar — next 30 days, capped at 5
        List<EventResponse> upcomingEvents = eventService.getVisibleFamilyEventsBetween(
                currentUser.getFamilyId(),
                today.atStartOfDay(),
                today.plusDays(30).atTime(23, 59, 59),
                currentUser
        ).stream().limit(5).toList();

        // Pending tasks for sidebar — TODO status only, capped at 5
        List<TaskItem> pendingTasks = taskService
                .getFamilyTasksByStatus(currentUser.getFamilyId(), TaskStatus.TODO)
                .stream().limit(5).toList();

        model.addAttribute("weeks", buildWeeks(calStart, calEnd, viewDate, events, tasks, today));
        model.addAttribute("monthLabel", monthLabel);
        model.addAttribute("prevMonth", viewDate.minusMonths(1));
        model.addAttribute("nextMonth", viewDate.plusMonths(1));
        model.addAttribute("upcomingEvents", upcomingEvents);
        model.addAttribute("pendingTasks", pendingTasks);

        return "dashboard";
    }

    // Builds the calendar grid as a list of weeks, each containing exactly 7 CalendarDay entries
    private List<List<CalendarDay>> buildWeeks(
            LocalDate calStart,
            LocalDate calEnd,
            LocalDate viewDate,
            List<EventResponse> events,
            List<TaskItem> tasks,
            LocalDate today
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
                        day.equals(today)
                ));
                current = current.plusDays(1);
            }
            weeks.add(week);
        }

        return weeks;
    }
}
