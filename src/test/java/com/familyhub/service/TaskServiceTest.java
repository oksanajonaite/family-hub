package com.familyhub.service;

import com.familyhub.entity.Family;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.mapper.TaskMapper;
import com.familyhub.repository.FamilyMemberRepository;
import com.familyhub.repository.TaskRepository;
import com.familyhub.repository.UserRepository;
import com.familyhub.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TaskService business logic.
 *
 * What we test here:
 *  - Role-based access: PARENT can do everything, KID has restrictions
 *  - Family isolation: users cannot touch tasks from other families
 *  - completedAt field behaviour when status changes to/from DONE
 *
 * What we don't test here (not logic, just DB calls):
 *  - getFamilyTasks(), getTaskById() — trivial repository delegation
 *
 * Tools used:
 *  - @Mock — creates a fake (mock) object; no real DB, no real service called
 *  - @InjectMocks — creates the real TaskService and injects the mocks into it
 *  - when(...).thenReturn(...) — tells the mock what to return when called
 *  - assertThatThrownBy — checks that the correct exception is thrown
 *  - verify(...) — checks that a method was actually called on a mock
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    // These are all fake (mock) objects — no real DB or Spring context
    @Mock TaskRepository taskRepository;
    @Mock UserRepository userRepository;
    @Mock FamilyMemberRepository familyMemberRepository;
    @Mock TaskMapper taskMapper;
    @Mock NotificationService notificationService;

    // TaskService is the real class being tested — mocks above are injected into it
    @InjectMocks TaskService taskService;

    private static final Long FAMILY_ID = 1L;
    private static final Long OTHER_FAMILY_ID = 2L;
    private static final Long TASK_ID = 10L;
    private static final Long USER_ID = 42L;

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a fake logged-in user with the given id, familyId and role.
     * lenient() is needed because not every test calls all three methods —
     * e.g. when an exception is thrown early, getRole() is never reached.
     * Without lenient(), Mockito would fail with UnnecessaryStubbingException.
     */
    private CustomUserDetails mockUser(Long id, Long familyId, Role role) {
        CustomUserDetails user = mock(CustomUserDetails.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getFamilyId()).thenReturn(familyId);
        lenient().when(user.getRole()).thenReturn(role);
        return user;
    }

    /** Creates a minimal User entity with just an id set. */
    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    /**
     * Builds a TaskItem that belongs to the given family, with the given assigned users.
     * Family is mocked so we can control getId() without touching the DB.
     */
    private TaskItem taskInFamily(Long familyId, List<User> assignedUsers) {
        Family family = mock(Family.class);
        when(family.getId()).thenReturn(familyId);

        TaskItem task = new TaskItem();
        task.setId(TASK_ID);
        task.setFamily(family);
        task.setTitle("Test task");
        task.setAssignedUsers(new ArrayList<>(assignedUsers));
        task.setAssignedMembers(new ArrayList<>());
        return task;
    }

    // =========================================================================
    // updateStatus — role-based access
    // =========================================================================

    /** PARENT has no restrictions — can change status of any task in their family. */
    @Test
    void updateStatus_parentCanChangeAnyTaskStatus() {
        TaskItem task = taskInFamily(FAMILY_ID, List.of());
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        CustomUserDetails parent = mockUser(USER_ID, FAMILY_ID, Role.PARENT);

        assertThatNoException().isThrownBy(() ->
                taskService.updateStatus(TASK_ID, TaskStatus.IN_PROGRESS, parent));
    }

    /** KID can update status only if they are in the task's assignedUsers list. */
    @Test
    void updateStatus_kidCanChangeStatus_whenAssigned() {
        User kid = userWithId(USER_ID);
        TaskItem task = taskInFamily(FAMILY_ID, List.of(kid));
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        CustomUserDetails kidUser = mockUser(USER_ID, FAMILY_ID, Role.KID);

        assertThatNoException().isThrownBy(() ->
                taskService.updateStatus(TASK_ID, TaskStatus.DONE, kidUser));
    }

    /** KID is assigned a different user's task — should be blocked. */
    @Test
    void updateStatus_kidThrowsAccessDenied_whenAssignedToSomeoneElse() {
        User otherUser = userWithId(99L);
        TaskItem task = taskInFamily(FAMILY_ID, List.of(otherUser));
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        CustomUserDetails kidUser = mockUser(USER_ID, FAMILY_ID, Role.KID);

        assertThatThrownBy(() -> taskService.updateStatus(TASK_ID, TaskStatus.DONE, kidUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** KID tries to update a task that has no assignees at all — should be blocked. */
    @Test
    void updateStatus_kidThrowsAccessDenied_whenNoOneAssigned() {
        TaskItem task = taskInFamily(FAMILY_ID, List.of());
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        CustomUserDetails kidUser = mockUser(USER_ID, FAMILY_ID, Role.KID);

        assertThatThrownBy(() -> taskService.updateStatus(TASK_ID, TaskStatus.DONE, kidUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =========================================================================
    // updateStatus — family isolation
    // =========================================================================

    /**
     * A task exists in family 2, but the user belongs to family 1.
     * The service must reject this — users cannot touch other families' data.
     */
    @Test
    void updateStatus_taskFromDifferentFamily_throwsAccessDenied() {
        TaskItem task = taskInFamily(OTHER_FAMILY_ID, List.of());
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        CustomUserDetails parent = mockUser(USER_ID, FAMILY_ID, Role.PARENT);

        assertThatThrownBy(() -> taskService.updateStatus(TASK_ID, TaskStatus.DONE, parent))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =========================================================================
    // updateStatus — completedAt field
    // =========================================================================

    /** When status is set to DONE, completedAt must be filled in automatically. */
    @Test
    void updateStatus_settingDone_setsCompletedAt() {
        TaskItem task = taskInFamily(FAMILY_ID, List.of());
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateStatus(TASK_ID, TaskStatus.DONE, mockUser(USER_ID, FAMILY_ID, Role.PARENT));

        assertThat(task.getCompletedAt()).isNotNull();
    }

    /** When status is changed back from DONE to TODO, completedAt must be cleared. */
    @Test
    void updateStatus_settingTodoFromDone_clearsCompletedAt() {
        TaskItem task = taskInFamily(FAMILY_ID, List.of());
        task.setCompletedAt(LocalDateTime.now()); // simulate: task was previously DONE
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateStatus(TASK_ID, TaskStatus.TODO, mockUser(USER_ID, FAMILY_ID, Role.PARENT));

        assertThat(task.getCompletedAt()).isNull();
    }

    // =========================================================================
    // deleteTask
    // =========================================================================

    /** KID role is not allowed to delete tasks — only PARENT can. */
    @Test
    void deleteTask_kidThrowsAccessDenied() {
        TaskItem task = taskInFamily(FAMILY_ID, List.of());
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.deleteTask(TASK_ID, mockUser(USER_ID, FAMILY_ID, Role.KID)))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** PARENT can delete a task — verify that repository.delete() was actually called. */
    @Test
    void deleteTask_parentCanDeleteTask() {
        TaskItem task = taskInFamily(FAMILY_ID, List.of());
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

        assertThatNoException().isThrownBy(() ->
                taskService.deleteTask(TASK_ID, mockUser(USER_ID, FAMILY_ID, Role.PARENT)));

        // verify() checks that delete() was actually called with the correct task object
        verify(taskRepository).delete(task);
    }
}
