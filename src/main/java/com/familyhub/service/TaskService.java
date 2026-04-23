package com.familyhub.service;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.dto.request.task.UpdateTaskRequest;
import com.familyhub.dto.response.task.TaskFormData;
import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.NotificationType;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.TaskNotFoundException;
import com.familyhub.mapper.TaskMapper;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.TaskRepository;
import com.familyhub.repository.UserRepository;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final TaskMapper taskMapper;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Transactional
    public TaskItem createTask(CreateTaskRequest request, CustomUserDetails currentUser) {
        User creator = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        TaskItem task = taskMapper.toEntity(request);
        task.setFamily(creator.getFamily());
        task.setCreatedBy(creator);

        // Only PARENT can assign tasks to others
        if (request.assigneeIds() != null && !request.assigneeIds().isEmpty()) {
            if (currentUser.getRole() != Role.PARENT) {
                throw new ForbiddenException();
            }
            applyAssignees(task, request.assigneeIds(), currentUser.getFamilyId());
        }

        TaskItem saved = taskRepository.save(task);

        // Send a notification to each assigned user, except the creator
        List<User> assignedUsers = saved.getAssignedUsers() != null
                ? saved.getAssignedUsers()
                : Collections.emptyList();

        for (User assignedUser : assignedUsers) {
            if (!assignedUser.getId().equals(currentUser.getId())) {
                notificationService.createNotification(
                        assignedUser,
                        NotificationType.TASK_ASSIGNED,
                        "You have been assigned a new task: \"" + saved.getTitle() + "\"",
                        "TASK",
                        saved.getId()
                );

                // Email is sent after the in-app notification so a DB failure does not block email.
                // Wrapped in try-catch — email delivery is best-effort; a mail server issue
                // must not roll back the task creation transaction.
                // Skipped entirely if the user has opted out of email notifications.
                if (assignedUser.isEmailNotificationsEnabled()) {
                    try {
                        emailService.sendTaskAssigned(
                                assignedUser.getEmail(),
                                assignedUser.getDisplayName(),
                                saved.getTitle(),
                                currentUser.getDisplayName()
                        );
                    } catch (Exception e) {
                        log.warn("Failed to send task assignment email to {}: {}", assignedUser.getEmail(), e.getMessage());
                    }
                }
            }
        }

        return saved;
    }

    @Transactional
    public TaskItem updateTask(Long taskId, UpdateTaskRequest request, CustomUserDetails currentUser) {
        TaskItem task = getTaskBelongingToFamily(taskId, currentUser.getFamilyId());

        if (currentUser.getRole() != Role.PARENT) {
            throw new ForbiddenException();
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
                throw new ForbiddenException();
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
            throw new ForbiddenException();
        }

        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public List<TaskItem> getFamilyTasks(Long familyId, CustomUserDetails currentUser) {
        List<TaskItem> tasks = taskRepository.findAllByFamilyIdOrderByCreatedAtDesc(familyId);
        initializeTaskRelations(tasks);
        return tasks.stream()
                .filter(t -> isTaskVisible(t, currentUser.getId(), currentUser.getRole() == Role.PARENT))
                .toList();
    }

    // Returns tasks with a due date in the given range — used by the calendar view.
    // The .size() calls are intentional: they force-initialize LAZY collections while
    // the @Transactional session is still open. Without this, Thymeleaf would throw
    // LazyInitializationException when trying to render them after the transaction closes.
    // Initialize required relations while the transaction is open so Thymeleaf can render them safely.
    @Transactional(readOnly = true)
    public List<TaskItem> getFamilyTasksBetween(Long familyId, LocalDate from, LocalDate to, Long currentUserId, boolean isParent) {
        List<TaskItem> tasks = taskRepository.findAllByFamilyIdAndDueDateBetween(familyId, from, to);
        initializeTaskRelations(tasks);
        return tasks.stream()
                .filter(t -> isTaskVisible(t, currentUserId, isParent))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskItem> getFamilyTasksByStatus(Long familyId, TaskStatus status, CustomUserDetails currentUser) {
        List<TaskItem> tasks = taskRepository.findAllByFamilyIdAndStatusOrderByCreatedAtDesc(familyId, status);
        initializeTaskRelations(tasks);
        return tasks.stream()
                .filter(t -> isTaskVisible(t, currentUser.getId(), currentUser.getRole() == Role.PARENT))
                .toList();
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
        TaskItem task = getTaskBelongingToFamily(taskId, familyId);
        initializeTaskRelations(List.of(task));
        return task;
    }

    public UpdateTaskRequest toEditRequest(TaskItem task) {
        List<String> assigneeIds = new ArrayList<>();
        task.getAssignedUsers().forEach(u -> assigneeIds.add("USER_" + u.getId()));
        task.getAssignedMembers().forEach(m -> assigneeIds.add("MEMBER_" + m.getId()));
        return new UpdateTaskRequest(
                task.getTitle(), task.getDescription(), task.getPriority(),
                assigneeIds, task.getDueDate(), task.isPrivateTask()
        );
    }

    @Transactional(readOnly = true)
    public TaskFormData buildTaskFormData(Long taskId, List<String> assigneeIds, Long familyId) {
        return new TaskFormData(
                userRepository.findAllByFamilyId(familyId),
                familyMemberRepository.findAllByFamilyId(familyId),
                List.of(TaskPriority.values()),
                taskId,
                assigneeIds
        );
    }

    // Clears existing assignees and re-applies based on prefixed string identifiers.
    // "USER_42"   → added to assignedUsers
    // "MEMBER_15" → added to assignedMembers
    // null or empty list → both collections are cleared
    // Only assignees that belong to the same family are accepted (security check).
    private void applyAssignees(TaskItem task, List<String> assigneeIds, Long familyId) {
        if (assigneeIds == null || assigneeIds.isEmpty()) {
            task.setAssignedUsers(List.of());
            task.setAssignedMembers(List.of());
            return;
        }

        List<Long> userIds = new ArrayList<>();
        List<Long> memberIds = new ArrayList<>();

        for (String assigneeId : assigneeIds) {
            if (assigneeId.startsWith("USER_")) {
                userIds.add(Long.parseLong(assigneeId.substring(5)));
            } else if (assigneeId.startsWith("MEMBER_")) {
                memberIds.add(Long.parseLong(assigneeId.substring(7)));
            }
        }

        List<User> users = userRepository.findAllById(userIds).stream()
                .filter(u -> u.getFamily() != null && familyId.equals(u.getFamily().getId()))
                .toList();

        List<FamilyMember> members = familyMemberRepository.findAllById(memberIds).stream()
                .filter(m -> m.getFamily() != null && familyId.equals(m.getFamily().getId()))
                .toList();

        task.setAssignedUsers(users);
        task.setAssignedMembers(members);
    }

    // A private task is visible only to its creator and any PARENT in the family.
    // KID users who didn't create the task won't see it in lists or the calendar.
    private boolean isTaskVisible(TaskItem task, Long currentUserId, boolean isParent) {
        if (!task.isPrivateTask()) return true;
        if (isParent) return true;
        return task.getCreatedBy().getId().equals(currentUserId);
    }

    private TaskItem getTaskBelongingToFamily(Long taskId, Long familyId) {
        TaskItem task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        if (!task.getFamily().getId().equals(familyId)) {
            throw new ForbiddenException();
        }

        return task;
    }

    private void initializeTaskRelations(List<TaskItem> tasks) {
        tasks.forEach(task -> {
            task.getAssignedUsers().size();
            task.getAssignedMembers().size();
            task.getCreatedBy().getId();
            task.getFamily().getId();
        });
    }
}
