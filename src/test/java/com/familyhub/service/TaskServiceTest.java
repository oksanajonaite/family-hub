package com.familyhub.service;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.entity.Family;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.exception.ForbiddenException;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private FamilyMemberRepository familyMemberRepository;
    @Mock private TaskMapper taskMapper;
    @Mock private NotificationService notificationService;
    @Mock private EmailService emailService;

    @InjectMocks
    private TaskService taskService;

    // Pagalbinis metodas — sukuria KID CustomUserDetails objektą.
    // CustomUserDetails reikalauja User entity — todėl pirmiausia sukuriame User.
    // Šis pattern'as naudojamas visuose testuose kuriems reikia autentifikuoto vartotojo.
    private CustomUserDetails buildKidUser(Long userId, Long familyId) {
        User kidUser = User.builder()
                .id(userId)
                .email("kid@test.com")
                .displayName("Kid User")
                .password("hashed_password")
                .role(Role.KID)
                .family(Family.builder().id(familyId).build())
                .enabled(true)
                .build();
        return new CustomUserDetails(kidUser);
    }

    private CustomUserDetails buildParentUser(Long userId, Long familyId) {
        User parentUser = User.builder()
                .id(userId)
                .email("parent@test.com")
                .displayName("Parent User")
                .password("hashed_password")
                .role(Role.PARENT)
                .family(Family.builder().id(familyId).build())
                .enabled(true)
                .build();
        return new CustomUserDetails(parentUser);
    }

    // ── Test 1 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: KID negali priskirti užduoties kitiems.
    // TaskService leidžia KID kurti asmenines užduotis (be priskyrimo),
    // bet blokuoja jei KID bando nurodyti assigneeIds.
    // Šis testas tikrina kad tas blokavimas tikrai veikia.
    @Test
    void createTask_whenKidTriesToAssignTask_throwsForbiddenException() {
        // Arrange
        Long kidId = 2L;
        Long familyId = 10L;
        CustomUserDetails kid = buildKidUser(kidId, familyId);

        // createTask() pirmiausia kviečia userRepository.findById() —
        // reikia užmock'inti, kitaip IllegalStateException "User not found"
        User kidEntity = User.builder()
                .id(kidId)
                .role(Role.KID)
                .family(Family.builder().id(familyId).build())
                .build();
        when(userRepository.findById(kidId)).thenReturn(Optional.of(kidEntity));
        when(taskMapper.toEntity(any())).thenReturn(new TaskItem());

        // KID pateikia užduotį su assigneeIds — tai PARENT privilegija
        CreateTaskRequest requestWithAssignee = new CreateTaskRequest(
                "Clean room",
                null,
                TaskPriority.LOW,
                List.of("USER_3"), // bando priskirti kitam vartotojui
                null,
                false
        );

        // Act & Assert
        assertThrows(ForbiddenException.class,
                () -> taskService.createTask(requestWithAssignee, kid));

        // Užduotis NIEKADA neturėtų būti išsaugota jei meta exception
        verify(taskRepository, never()).save(any());
    }

    // ── Test 2 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: KID gali keisti statusą TIK savo priskirtoms užduotims.
    // Jei užduotis nepriskirta šiam KID — ForbiddenException.
    // Tai buvo rasta manualia patikra — šis testas automatizuoja tą patikrinimą.
    @Test
    void updateStatus_whenKidUpdatesTaskNotAssignedToThem_throwsForbiddenException() {
        // Arrange
        Long familyId = 10L;
        CustomUserDetails kid = buildKidUser(2L, familyId);

        Family family = Family.builder().id(familyId).build();

        // Užduotis egzistuoja, priklauso tai pačiai šeimai,
        // bet assignedUsers sąraše nėra šio KID
        TaskItem task = TaskItem.builder()
                .id(1L)
                .title("Wash dishes")
                .family(family)
                .status(TaskStatus.TODO)
                .assignedUsers(List.of()) // priskirta tuščiam sąrašui — KID čia nėra
                .assignedMembers(List.of())
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        // Act & Assert
        assertThrows(ForbiddenException.class,
                () -> taskService.updateStatus(1L, TaskStatus.IN_PROGRESS, kid));
    }

    // ── Test 3 ────────────────────────────────────────────────────────────────
    // Teigiamas testas (positive test) — KID GALI keisti statusą
    // jei užduotis yra priskirta jam.
    // Svarbu turėti ir teigiamą testą — patvirtina kad apsauga neblokuoja teisėtų veiksmų.
    @Test
    void updateStatus_whenKidUpdatesTheirOwnTask_doesNotThrow() {
        // Arrange
        Long familyId = 10L;
        Long kidId = 2L;
        CustomUserDetails kid = buildKidUser(kidId, familyId);

        Family family = Family.builder().id(familyId).build();

        // KID vartotojas kaip entity — reikalingas assignedUsers sąrašui
        User kidEntity = User.builder()
                .id(kidId)
                .role(Role.KID)
                .family(family)
                .build();

        TaskItem task = TaskItem.builder()
                .id(1L)
                .title("Do homework")
                .family(family)
                .status(TaskStatus.TODO)
                .assignedUsers(List.of(kidEntity)) // KID priskirtas šiai užduočiai
                .assignedMembers(List.of())
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(task);

        // Act & Assert — jokio exception neturėtų būti
        // assertDoesNotThrow naudojamas kai tikriname kad viskas veikia normaliai
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> taskService.updateStatus(1L, TaskStatus.IN_PROGRESS, kid));
    }

    // ── Test 4 ────────────────────────────────────────────────────────────────
    // Saugumo taisyklė: tik PARENT gali trinti užduotis.
    // KID negali trinti net savo priskirtų užduočių.
    @Test
    void deleteTask_whenKidTriesToDelete_throwsForbiddenException() {
        // Arrange
        Long familyId = 10L;
        CustomUserDetails kid = buildKidUser(2L, familyId);

        TaskItem task = TaskItem.builder()
                .id(1L)
                .title("Task to delete")
                .family(Family.builder().id(familyId).build())
                .assignedUsers(List.of())
                .assignedMembers(List.of())
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        // Act & Assert
        assertThrows(ForbiddenException.class,
                () -> taskService.deleteTask(1L, kid));

        // Užduotis NIEKADA neturėtų būti ištrinta
        verify(taskRepository, never()).delete(any());
    }
}
