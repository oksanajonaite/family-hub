package com.familyhub.service;

import com.familyhub.dto.response.BirthdayEntry;
import com.familyhub.dto.response.CalendarDay;

import java.time.LocalDate;
import java.util.List;

public record DashboardCalendarData(
        List<List<CalendarDay>> weeks,
        String monthLabel,
        LocalDate prevMonth,
        LocalDate nextMonth,
        LocalDate selectedDate,
        List<BirthdayEntry> upcomingBirthdays
) {}
