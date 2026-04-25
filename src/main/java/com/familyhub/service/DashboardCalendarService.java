package com.familyhub.service;

import com.familyhub.dto.response.calendar.BirthdayEntry;
import com.familyhub.dto.response.calendar.CalendarDay;
import com.familyhub.dto.response.holiday.HolidayEntry;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.UserRepository;
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
public class DashboardCalendarService {

    private final EventService eventService;
    private final TaskService taskService;
    private final PublicHolidayService publicHolidayService;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public DashboardCalendarData buildCalendarData(
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

        LocalDate calStart = viewDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate calEnd = viewDate.with(TemporalAdjusters.lastDayOfMonth())
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        LocalDate selectedDate = resolveSelectedDate(selected, viewDate, today, calStart, calEnd);

        // Events and tasks still come from our own application data.
        List<EventResponse> calendarEvents = eventService.getVisibleFamilyEventsBetween(
                familyId, calStart.atStartOfDay(), calEnd.atTime(23, 59, 59), currentUser);

        List<TaskItem> calendarTasks = taskService.getFamilyTasksBetween(
                        familyId, calStart, calEnd,
                        currentUser.getId(), currentUser.getRole() == Role.PARENT)
                .stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE)
                .toList();

        List<BirthdayEntry> allBirthdays = collectFamilyBirthdays(familyId, currentUser.getId());
        // Public holidays are external reference data layered on top of the same calendar view.
        List<HolidayEntry> holidays = publicHolidayService.getLithuanianHolidaysBetween(calStart, calEnd);

        LocalDate tomorrow = today.plusDays(1);
        List<BirthdayEntry> upcomingBirthdays = allBirthdays.stream()
                .filter(b -> matchesDayOf(b.dateOfBirth(), today) || matchesDayOf(b.dateOfBirth(), tomorrow))
                .toList();
        List<HolidayEntry> upcomingHolidays = holidays.stream()
                .filter(h -> h.date().equals(today) || h.date().equals(tomorrow))
                .toList();

        String monthLabel = viewDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));

        return new DashboardCalendarData(
                buildWeeks(calStart, calEnd, viewDate, calendarEvents, calendarTasks, allBirthdays, holidays, today, selectedDate),
                monthLabel,
                viewDate.minusMonths(1),
                viewDate.plusMonths(1),
                selectedDate,
                upcomingBirthdays,
                upcomingHolidays
        );
    }

    private List<BirthdayEntry> collectFamilyBirthdays(Long familyId, Long currentUserId) {
        if (familyId == null) {
            return userRepository.findById(currentUserId)
                    .filter(u -> u.getDateOfBirth() != null)
                    .map(u -> List.of(new BirthdayEntry(u.getDisplayName(), u.getDateOfBirth())))
                    .orElse(List.of());
        }

        List<BirthdayEntry> birthdays = new ArrayList<>();

        List<User> users = userRepository.findAllByFamilyId(familyId);
        for (User user : users) {
            if (user.getDateOfBirth() != null) {
                birthdays.add(new BirthdayEntry(user.getDisplayName(), user.getDateOfBirth()));
            }
        }

        List<FamilyMember> members = familyMemberRepository.findAllByFamilyId(familyId);
        for (FamilyMember member : members) {
            if (member.getDateOfBirth() != null) {
                birthdays.add(new BirthdayEntry(member.getName(), member.getDateOfBirth()));
            }
        }

        return birthdays;
    }

    private boolean matchesDayOf(LocalDate dateOfBirth, LocalDate date) {
        return dateOfBirth.getMonthValue() == date.getMonthValue()
                && dateOfBirth.getDayOfMonth() == date.getDayOfMonth();
    }

    private LocalDate resolveSelectedDate(LocalDate selected, LocalDate viewDate, LocalDate today,
                                          LocalDate calStart, LocalDate calEnd) {
        if (selected != null) {
            return (!selected.isBefore(calStart) && !selected.isAfter(calEnd))
                    ? selected
                    : viewDate;
        }
        boolean isCurrentMonth = viewDate.getYear() == today.getYear()
                && viewDate.getMonth() == today.getMonth();
        return isCurrentMonth ? today : viewDate;
    }

    private List<List<CalendarDay>> buildWeeks(
            LocalDate calStart,
            LocalDate calEnd,
            LocalDate viewDate,
            List<EventResponse> events,
            List<TaskItem> tasks,
            List<BirthdayEntry> allBirthdays,
            List<HolidayEntry> holidays,
            LocalDate today,
            LocalDate selectedDate
    ) {
        List<List<CalendarDay>> weeks = new ArrayList<>();
        LocalDate current = calStart;

        while (!current.isAfter(calEnd)) {
            List<CalendarDay> week = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                LocalDate day = current;
                // Build one day view model by grouping each data source onto the same date.
                List<EventResponse> dayEvents = events.stream()
                        .filter(event -> event.startsAt().toLocalDate().equals(day))
                        .toList();
                List<TaskItem> dayTasks = tasks.stream()
                        .filter(task -> day.equals(task.getDueDate()))
                        .toList();
                List<BirthdayEntry> dayBirthdays = allBirthdays.stream()
                        .filter(birthday -> matchesDayOf(birthday.dateOfBirth(), day))
                        .toList();
                List<HolidayEntry> dayHolidays = holidays.stream()
                        .filter(holiday -> holiday.date().equals(day))
                        .toList();
                week.add(new CalendarDay(
                        day,
                        dayEvents,
                        dayTasks,
                        dayBirthdays,
                        dayHolidays,
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
