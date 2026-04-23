package com.familyhub.service;

import com.familyhub.dto.response.CalendarViewModel;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

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

        return new CalendarViewModel(
                calendarData.weeks(),
                calendarData.monthLabel(),
                calendarData.prevMonth(),
                calendarData.nextMonth(),
                calendarData.selectedDate(),
                sidebarData.upcomingEvents(),
                calendarData.upcomingBirthdays(),
                sidebarData.dueSoonTasks(),
                sidebarData.todayEventsCount(),
                sidebarData.attentionTasksCount(),
                sidebarData.doneCount(),
                sidebarData.totalCount()
        );
    }
}
