package com.familyhub.service;

import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.Family;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.EventType;
import com.familyhub.entity.enums.RecurrenceType;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSidebarServiceTest {

    @Mock private EventService eventService;
    @Mock private TaskService taskService;

    @InjectMocks
    private DashboardSidebarService dashboardSidebarService;

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

    private EventResponse eventAt(LocalDateTime startsAt) {
        return new EventResponse(1L, "Event", null, startsAt, startsAt.plusHours(1),
                false, EventType.OTHER, RecurrenceType.NONE, null, 1L,
                List.of(), List.of(), List.of(), List.of());
    }

    private TaskItem task(TaskStatus status, TaskPriority priority, LocalDate dueDate) {
        return TaskItem.builder()
                .id((long) (Math.random() * 1000))
                .title("Task")
                .status(status)
                .priority(priority)
                .dueDate(dueDate)
                .build();
    }

    // Tikrina, kad todayEventsCount skaičiuoja tik šiandien prasidedančius renginius.
    // Rytojaus renginiai turi patekti į upcomingEvents sąrašą (matomi vartotojui),
    // bet neturi didinti šiandienos skaitliuko šoninėje juostoje.
    @Test
    void buildSidebarData_todayEventsCount_excludesTomorrowEvents() {
        Long familyId = 10L;
        CustomUserDetails user = buildUser(1L, familyId);
        LocalDate today = LocalDate.now();

        List<EventResponse> events = List.of(
                eventAt(today.atTime(9, 0)),
                eventAt(today.atTime(14, 0)),
                eventAt(today.plusDays(1).atTime(10, 0))  // rytoj — neturi skaičiuotis
        );

        when(eventService.getVisibleFamilyEventsBetween(eq(familyId), any(), any(), eq(user)))
                .thenReturn(events);
        when(taskService.getFamilyTasks(eq(familyId), eq(user)))
                .thenReturn(List.of());

        DashboardSidebarData result = dashboardSidebarService.buildSidebarData(familyId, user);

        assertEquals(2, result.todayEventsCount());
        assertEquals(3, result.upcomingEvents().size());  // visi 3 matomi, limitas 4
    }

    // Tikrina attentionTasksCount logiką — "dėmesio reikalaujančios" užduotys yra:
    // HIGH prioriteto ARBA dueDate per artimiausias 2 dienas (bet ne DONE statusas).
    // Šis skaičius rodomas paryškintu šriftu šoninėje juostoje kaip skubūs darbai.
    @Test
    void buildSidebarData_attentionTasksCount_includesHighPriorityAndDueSoon() {
        Long familyId = 10L;
        CustomUserDetails user = buildUser(1L, familyId);
        LocalDate today = LocalDate.now();

        List<TaskItem> tasks = List.of(
                task(TaskStatus.TODO, TaskPriority.HIGH,   today.plusDays(10)),  // HIGH → attention
                task(TaskStatus.TODO, TaskPriority.MEDIUM, today),               // due today → attention
                task(TaskStatus.TODO, TaskPriority.MEDIUM, today.plusDays(10)),  // per toli → ne
                task(TaskStatus.DONE, TaskPriority.HIGH,   today)                // DONE → ne (nepateks į todoTasks)
        );

        when(eventService.getVisibleFamilyEventsBetween(eq(familyId), any(), any(), eq(user)))
                .thenReturn(List.of());
        when(taskService.getFamilyTasks(eq(familyId), eq(user)))
                .thenReturn(tasks);

        DashboardSidebarData result = dashboardSidebarService.buildSidebarData(familyId, user);

        assertEquals(2, result.attentionTasksCount());
        assertEquals(4, result.totalCount());
        assertEquals(1, result.doneCount());
    }

    // Tikrina, kad dueSoonTasks (rodomi šoninėje juostoje) neviršija 4 elementų,
    // nors atitinkančių užduočių gali būti daugiau.
    // Taip pat tikrina, kad DONE statusas neįtraukiamas į "artimų" sąrašą.
    @Test
    void buildSidebarData_dueSoonTasks_limitedToFourAndExcludesDone() {
        Long familyId = 10L;
        CustomUserDetails user = buildUser(1L, familyId);
        LocalDate today = LocalDate.now();

        List<TaskItem> tasks = List.of(
                task(TaskStatus.TODO,        TaskPriority.MEDIUM, today),
                task(TaskStatus.TODO,        TaskPriority.MEDIUM, today),
                task(TaskStatus.TODO,        TaskPriority.MEDIUM, today),
                task(TaskStatus.TODO,        TaskPriority.MEDIUM, today),
                task(TaskStatus.TODO,        TaskPriority.MEDIUM, today),  // 5-as — turi būti nupjautas
                task(TaskStatus.DONE,        TaskPriority.MEDIUM, today)   // DONE — neturi patekti
        );

        when(eventService.getVisibleFamilyEventsBetween(eq(familyId), any(), any(), eq(user)))
                .thenReturn(List.of());
        when(taskService.getFamilyTasks(eq(familyId), eq(user)))
                .thenReturn(tasks);

        DashboardSidebarData result = dashboardSidebarService.buildSidebarData(familyId, user);

        assertEquals(4, result.dueSoonTasks().size());
    }
}
