package com.familyhub.service;

import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardSidebarService {

    private final EventService eventService;
    private final TaskService taskService;

    public DashboardSidebarData buildSidebarData(Long familyId, CustomUserDetails currentUser) {
        LocalDate today = LocalDate.now();

        List<EventResponse> sidebarEvents = eventService.getVisibleFamilyEventsBetween(
                familyId, today.atStartOfDay(), today.plusDays(1).atTime(23, 59, 59), currentUser);
        List<EventResponse> upcomingEvents = sidebarEvents.stream().limit(4).toList();
        long todayEventsCount = sidebarEvents.stream()
                .filter(event -> !event.startsAt().toLocalDate().isAfter(today))
                .count();

        List<TaskItem> allTasks = taskService.getFamilyTasks(familyId, currentUser);
        List<TaskItem> todoTasks = allTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.TODO)
                .toList();
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
        long doneCount = allTasks.stream()
                .filter(task -> task.getStatus() == TaskStatus.DONE)
                .count();

        return new DashboardSidebarData(
                upcomingEvents,
                todayEventsCount,
                dueSoonTasks,
                attentionTasksCount,
                (int) doneCount,
                allTasks.size()
        );
    }
}
