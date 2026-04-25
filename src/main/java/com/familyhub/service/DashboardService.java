package com.familyhub.service;

import com.familyhub.dto.response.calendar.CalendarViewModel;
import com.familyhub.dto.response.calendar.CalendarDay;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

// Facade — single entry point for dashboard data assembly.
// DashboardCalendarService builds the calendar grid and birthday data;
// DashboardSidebarService builds the sidebar counters and task/event lists.
// This class combines both results into one CalendarViewModel for the controller.
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardCalendarService dashboardCalendarService;
    private final DashboardSidebarService dashboardSidebarService;

    public CalendarViewModel buildCalendarViewModel(
            Integer year,
            Integer month,
            LocalDate selected,
            CustomUserDetails currentUser
    ) {
        DashboardCalendarData calendarData =
                dashboardCalendarService.buildCalendarData(year, month, selected, currentUser);
        DashboardSidebarData sidebarData =
                dashboardSidebarService.buildSidebarData(currentUser.getFamilyId(), currentUser);

        List<CalendarDay> allDays = calendarData.weeks().stream()
                .flatMap(List::stream)
                .toList();

        CalendarDay selectedDay = allDays.stream()
                .filter(day -> day.date().equals(calendarData.selectedDate()))
                .findFirst()
                .orElseGet(() -> allDays.isEmpty()
                        ? emptyCalendarDay(calendarData.selectedDate(), calendarData.selectedDate())
                        : allDays.get(0));

        var dayByDate = allDays.stream()
                .collect(Collectors.toMap(CalendarDay::date, Function.identity(), (left, right) -> left));

        LocalDate mobileStart = calendarData.selectedDate().minusDays(3);
        List<CalendarDay> mobileDays = mobileStart
                .datesUntil(mobileStart.plusDays(7))
                .map(day -> dayByDate.getOrDefault(day, emptyCalendarDay(day, calendarData.selectedDate())))
                .toList();
        LocalDate mobilePrevDate = calendarData.selectedDate().minusDays(7);
        LocalDate mobileNextDate = calendarData.selectedDate().plusDays(7);

        return new CalendarViewModel(
                calendarData.weeks(),
                mobileDays,
                selectedDay,
                mobilePrevDate,
                mobileNextDate,
                calendarData.monthLabel(),
                calendarData.prevMonth(),
                calendarData.nextMonth(),
                calendarData.selectedDate(),
                sidebarData.upcomingEvents(),
                calendarData.upcomingBirthdays(),
                calendarData.upcomingHolidays(),
                sidebarData.dueSoonTasks(),
                sidebarData.todayEventsCount(),
                sidebarData.attentionTasksCount(),
                sidebarData.doneCount(),
                sidebarData.totalCount()
        );
    }

    private CalendarDay emptyCalendarDay(LocalDate day, LocalDate selectedDate) {
        return new CalendarDay(
                day,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                day.equals(LocalDate.now()),
                day.equals(selectedDate)
        );
    }
}
