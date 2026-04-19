package com.familyhub.service;

import com.familyhub.dto.response.BirthdayEntry;
import com.familyhub.dto.response.CalendarDay;
import com.familyhub.dto.response.CalendarViewModel;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskPriority;
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
public class DashboardService {

    private final EventService eventService;
    private final TaskService taskService;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;

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

        List<TaskItem> calendarTasks = taskService.getFamilyTasksBetween(
                        familyId, calStart, calEnd,
                        currentUser.getId(), currentUser.getRole() == Role.PARENT)
                .stream()
                .filter(task -> task.getStatus() != TaskStatus.DONE)
                .toList();

        // Collect all birthdays — family members if in a family, or just own birthday if not.
        List<BirthdayEntry> allBirthdays = collectFamilyBirthdays(familyId, currentUser.getId());

        // Birthdays showing today or tomorrow — used by the Today+Tomorrow sidebar widget
        LocalDate tomorrow = today.plusDays(1);
        List<BirthdayEntry> upcomingBirthdays = allBirthdays.stream()
                .filter(b -> matchesDayOf(b.dateOfBirth(), today) || matchesDayOf(b.dateOfBirth(), tomorrow))
                .toList();

        // Format month name in English — JVM default locale (Lithuanian) would produce Lithuanian names
        String monthLabel = viewDate.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));

        // Sidebar widgets: upcoming events, task counters, due-soon list
        SidebarData sidebar = buildSidebarData(familyId, today, currentUser);

        return new CalendarViewModel(
                buildWeeks(calStart, calEnd, viewDate, calendarEvents, calendarTasks, allBirthdays, today, selectedDate),
                monthLabel,
                viewDate.minusMonths(1),
                viewDate.plusMonths(1),
                selectedDate,
                sidebar.upcomingEvents(),
                upcomingBirthdays,
                sidebar.dueSoonTasks(),
                sidebar.todayEventsCount(),
                sidebar.attentionTasksCount(),
                sidebar.doneCount(),
                sidebar.totalCount()
        );
    }

    // Holds all sidebar widget data computed from a single set of DB queries.
    // Using a private record avoids passing 6+ values back from one method.
    // record — Java 16+ feature: immutable data carrier, auto-generates constructor/getters/equals/hashCode.
    private record SidebarData(
            List<EventResponse> upcomingEvents,
            long todayEventsCount,
            List<TaskItem> dueSoonTasks,
            long attentionTasksCount,
            int doneCount,
            int totalCount
    ) {}

    // Fetches and computes everything shown in the dashboard sidebar (right-side panels):
    // — upcoming events for today + tomorrow
    // — task counts and due-soon list
    // Extracted from buildCalendarViewModel() to keep that method focused on the calendar grid.
    private SidebarData buildSidebarData(Long familyId, LocalDate today, CustomUserDetails currentUser) {
        // 1 DB query for sidebar events (today + tomorrow), then split in memory
        List<EventResponse> sidebarEvents = eventService.getVisibleFamilyEventsBetween(
                familyId, today.atStartOfDay(), today.plusDays(1).atTime(23, 59, 59), currentUser);
        List<EventResponse> upcomingEvents = sidebarEvents.stream().limit(4).toList();
        long todayEventsCount = sidebarEvents.stream()
                .filter(e -> !e.startsAt().toLocalDate().isAfter(today))
                .count();

        // 1 DB query for all tasks, then split in memory
        List<TaskItem> allTasks = taskService.getFamilyTasks(familyId, currentUser);
        List<TaskItem> todoTasks = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).toList();
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
        long doneCount = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();

        return new SidebarData(upcomingEvents, todayEventsCount, dueSoonTasks, attentionTasksCount,
                (int) doneCount, allTasks.size());
    }

    // Loads all users and account-less members for the family and converts them to BirthdayEntry.
    // Only people with a non-null dateOfBirth are included.
    private List<BirthdayEntry> collectFamilyBirthdays(Long familyId, Long currentUserId) {
        // No family — show only the current user's own birthday (if set)
        if (familyId == null) {
            return userRepository.findById(currentUserId)
                    .filter(u -> u.getDateOfBirth() != null)
                    .map(u -> List.of(new BirthdayEntry(u.getDisplayName(), u.getDateOfBirth())))
                    .orElse(List.of());
        }

        List<BirthdayEntry> birthdays = new ArrayList<>();

        List<User> users = userRepository.findAllByFamilyId(familyId);
        for (User u : users) {
            if (u.getDateOfBirth() != null) {
                birthdays.add(new BirthdayEntry(u.getDisplayName(), u.getDateOfBirth()));
            }
        }

        List<FamilyMember> members = familyMemberRepository.findAllByFamilyId(familyId);
        for (FamilyMember fm : members) {
            if (fm.getDateOfBirth() != null) {
                birthdays.add(new BirthdayEntry(fm.getName(), fm.getDateOfBirth()));
            }
        }

        return birthdays;
    }

    // Checks if a birth date's month and day match the given date.
    // Year is intentionally ignored — birthdays recur annually.
    private boolean matchesDayOf(LocalDate dateOfBirth, LocalDate date) {
        return dateOfBirth.getMonthValue() == date.getMonthValue()
                && dateOfBirth.getDayOfMonth() == date.getDayOfMonth();
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
            List<BirthdayEntry> allBirthdays,
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
                // Match birthdays by month+day only — year is irrelevant
                List<BirthdayEntry> dayBirthdays = allBirthdays.stream()
                        .filter(b -> matchesDayOf(b.dateOfBirth(), day))
                        .toList();
                week.add(new CalendarDay(
                        day,
                        dayEvents,
                        dayTasks,
                        dayBirthdays,
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
