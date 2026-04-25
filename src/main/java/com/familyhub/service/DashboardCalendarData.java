package com.familyhub.service;

import com.familyhub.dto.response.calendar.BirthdayEntry;
import com.familyhub.dto.response.calendar.CalendarDay;
import com.familyhub.dto.response.holiday.HolidayEntry;

import java.time.LocalDate;
import java.util.List;

public record DashboardCalendarData(
        List<List<CalendarDay>> weeks,
        String monthLabel,
        LocalDate prevMonth,
        LocalDate nextMonth,
        LocalDate selectedDate,
        List<BirthdayEntry> upcomingBirthdays,
        List<HolidayEntry> upcomingHolidays
) {}
