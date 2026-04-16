package com.familyhub.service;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.dto.request.task.UpdateTaskRequest;
import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.NotificationType;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.exception.TaskNotFoundException;
import com.familyhub.mapper.TaskMapper;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.TaskRepository;
import com.familyhub.repository.UserRepository;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final TaskMapper taskMapper;
    private final NotificationService notificationService;

    @Transactional
    public TaskItem createTask(CreateTaskRequest request, CustomUserDetails currentUser) {
        User creator = userRepository.findById(currentUser.getId()).orElseThrow();

        TaskItem task = taskMapper.toEntity(request);
        task.setFamily(creator.getFamily());
        task.setCreatedBy(creator);

        // Only PARENT can assign tasks to others
        if (request.assigneeIds() != null && !request.assigneeIds().isEmpty()) {
            if (currentUser.getRole() != Role.PARENT) {
                throw new AccessDeniedException();
            }
            applyAssignees(task, request.assigneeIds(), currentUser.getFamilyId());
        }

        TaskItem saved = taskRepository.save(task);

        // Send a notification to each assigned user, except the creator
        for (User assignedUser : saved.getAssignedUsers()) {
            if (!assignedUser.getId().equals(currentUser.getId())) {
                notificationService.createNotification(
                        assignedUser,
                        NotificationType.TASK_ASSIGNED,
                        "You have been assigned a new task: \"" + saved.getTitle() + "\"",
                        "TASK",
                        saved.getId()
                );
            }
        }

        return saved;
    }

    @Transactional
    public TaskItem updateTask(Long taskId, UpdateTaskRequest request, CustomUserDetails currentUser) {
        TaskItem task = getTaskBelongingToFamily(taskId, currentUser.getFamilyId());

        if (currentUser.getRole() != Role.PARENT) {
            throw new AccessDeniedException();
        }

        taskMapper.updateEntity(request, task);
        applyAssignees(task, request.assigneeIds(), currentUser.getFamilyId());

        return taskRepository.save(task);
    }

    @Transactional
    public void updateStatus(Long taskId, TaskStatus newStatus, CustomUserDetails currentUser) {
        TaskItem task = getTaskBelongingToFamily(taskId, currentUser.getFamilyId());

        // KID can only update status if they are among the assigned users
        if (currentUser.getRole() == Role.KID) {
            boolean isAssignedToMe = task.getAssignedUsers().stream()
                    .anyMatch(u -> u.getId().equals(currentUser.getId()));
            if (!isAssignedToMe) {
                throw new AccessDeniedException();
            }
        }

        task.setStatus(newStatus);

        if (newStatus == TaskStatus.DONE) {
            task.setCompletedAt(LocalDateTime.now());
        } else {
            task.setCompletedAt(null);
        }

        taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long taskId, CustomUserDetails currentUser) {
        TaskItem task = getTaskBelongingToFamily(taskId, currentUser.getFamilyId());

        if (currentUser.getRole() != Role.PARENT) {
            throw new AccessDeniedException();
        }

        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public List<TaskItem> getFamilyTasks(Long familyId) {
        return taskRepository.findAllByFamilyIdOrderByCreatedAtDesc(familyId);
    }

    // Returns tasks with a due date in the given range — used by the calendar view.
    // The .size() calls are intentional: they force-initialize LAZY collections while
    // the @Transactional session is still open. Without this, Thymeleaf would throw
    // LazyInitializationException when trying to render them after the transaction closes.
    @Transactional(readOnly = true)
    public List<TaskItem> getFamilyTasksBetween(Long familyId, LocalDate from, LocalDate to) {
        List<TaskItem> tasks = taskRepository.findAllByFamilyIdAndDueDateBetween(familyId, from, to);
        tasks.forEach(t -> {
            t.getAssignedUsers().size();
            t.getAssignedMembers().size();
        });
        return tasks;
    }

    @Transactional(readOnly = true)
    public List<TaskItem> getFamilyTasksByStatus(Long familyId, TaskStatus status) {
        return taskRepository.findAllByFamilyIdAndStatusOrderByCreatedAtDesc(familyId, status);
    }

    @Transactional(readOnly = true)
    public TaskItem getTaskById(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    // Family-safe version of getTaskById — verifies the task belongs to the given family.
    // Use this whenever a task is fetched in response to a user action (edit, view, etc.)
    @Transactional(readOnly = true)
    public TaskItem getTaskByIdForFamily(Long taskId, Long familyId) {
        return getTaskBelongingToFamily(taskId, familyId);
    }

    // Clears existing assignees and re-applies based on prefixed string identifiers.
    // "USER_42"   → added to assignedUsers
    // "MEMBER_15" → added to assignedMembers
    // null or empty list → both collections are cleared
    // Only assignees that belong to the same family are accepted (security check).
    private void applyAssignees(TaskItem task, List<String> assigneeIds, Long familyId) {
        List<User> users = new ArrayList<>();
        List<FamilyMember> members = new ArrayList<>();

        if (assigneeIds != null) {
            for (String assigneeId : assigneeIds) {
                if (assigneeId.startsWith("USER_")) {
                    Long userId = Long.parseLong(assigneeId.substring(5));
                    userRepository.findById(userId)
                            .filter(u -> u.getFamily() != null && familyId.equals(u.getFamily().getId()))
                            .ifPresent(users::add);
                } else if (assigneeId.startsWith("MEMBER_")) {
                    Long memberId = Long.parseLong(assigneeId.substring(7));
                    familyMemberRepository.findById(memberId)
                            .filter(m -> m.getFamily() != null && familyId.equals(m.getFamily().getId()))
                            .ifPresent(members::add);
                }
            }
        }

        task.setAssignedUsers(users);
        task.setAssignedMembers(members);
    }

    private TaskItem getTaskBelongingToFamily(Long taskId, Long familyId) {
        TaskItem task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        if (!task.getFamily().getId().equals(familyId)) {
            throw new AccessDeniedException();
        }

        return task;
    }
}
