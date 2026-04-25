package com.familyhub.service;

import com.familyhub.dto.response.holiday.HolidayEntry;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.Family;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.EventType;
import com.familyhub.entity.enums.RecurrenceType;
import com.familyhub.entity.enums.Role;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardCalendarServiceTest {

    @Mock private EventService eventService;
    @Mock private TaskService taskService;
    @Mock private PublicHolidayService publicHolidayService;
    @Mock private UserRepository userRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;

    @InjectMocks
    private DashboardCalendarService dashboardCalendarService;

    private CustomUserDetails buildUser(Long userId, Long familyId) {
        User user = User.builder()
                .id(userId)
                .email("user@test.com")
                .displayName("Test User")
                .password("hashed")
                .role(Role.PARENT)
                .family(Family.builder().id(familyId).build())
                .enabled(true)
                .build();
        return new CustomUserDetails(user);
    }

    // Pagrindinis integracinės logikos testas šiame service sluoksnyje:
    // jei PublicHolidayService grąžina šventę konkrečiai datai,
    // DashboardCalendarService turi ją įdėti į tos dienos CalendarDay.holidays sąrašą.
    @Test
    void buildCalendarData_addsPublicHolidaysToMatchingCalendarDay() {
        LocalDate selectedDate = LocalDate.of(2026, 1, 1);
        CustomUserDetails currentUser = buildUser(1L, 10L);

        when(eventService.getVisibleFamilyEventsBetween(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(currentUser)
        )).thenReturn(List.of());

        when(taskService.getFamilyTasksBetween(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(List.of());

        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of());
        when(familyMemberRepository.findAllByFamilyId(10L)).thenReturn(List.of());
        when(publicHolidayService.getLithuanianHolidaysBetween(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(
                new HolidayEntry(selectedDate, "Naujieji metai", "New Year's Day")
        ));

        DashboardCalendarData result = dashboardCalendarService.buildCalendarData(2026, 1, selectedDate, currentUser);

        var matchingDay = result.weeks().stream()
                .flatMap(List::stream)
                .filter(day -> day.date().equals(selectedDate))
                .findFirst()
                .orElseThrow();

        assertEquals(1, matchingDay.holidays().size());
        assertEquals("Naujieji metai", matchingDay.holidays().get(0).localName());
    }

    // Neigiamas / stabilumo testas:
    // jei holiday sluoksnis nieko negrąžina, dashboard kalendorius vis tiek turi veikti normaliai.
    // Tai saugo nuo regressions, kai išorinis švenčių šaltinis tuščias arba laikinai neveikia.
    @Test
    void buildCalendarData_keepsCalendarWorkingWhenHolidayListIsEmpty() {
        LocalDate selectedDate = LocalDate.of(2026, 2, 10);
        CustomUserDetails currentUser = buildUser(1L, 10L);

        EventResponse familyEvent = new EventResponse(
                100L,
                "Doctor visit",
                null,
                selectedDate.atTime(9, 0),
                selectedDate.atTime(10, 0),
                false,
                EventType.MEDICAL,
                RecurrenceType.NONE,
                null,
                1L,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        when(eventService.getVisibleFamilyEventsBetween(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(currentUser)
        )).thenReturn(List.of(familyEvent));

        when(taskService.getFamilyTasksBetween(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(List.of());

        when(userRepository.findAllByFamilyId(10L)).thenReturn(List.of());
        when(familyMemberRepository.findAllByFamilyId(10L)).thenReturn(List.of());
        when(publicHolidayService.getLithuanianHolidaysBetween(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());

        DashboardCalendarData result = dashboardCalendarService.buildCalendarData(2026, 2, selectedDate, currentUser);

        var matchingDay = result.weeks().stream()
                .flatMap(List::stream)
                .filter(day -> day.date().equals(selectedDate))
                .findFirst()
                .orElseThrow();

        assertTrue(matchingDay.holidays().isEmpty());
        assertEquals(1, matchingDay.events().size());
        assertEquals("Doctor visit", matchingDay.events().get(0).title());
        assertFalse(result.weeks().isEmpty());
    }
}
