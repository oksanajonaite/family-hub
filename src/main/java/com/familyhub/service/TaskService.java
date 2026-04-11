package com.familyhub.service;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.dto.request.task.UpdateTaskRequest;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.exception.TaskNotFoundException;
import com.familyhub.mapper.TaskMapper;
import com.familyhub.repository.TaskRepository;
import com.familyhub.repository.UserRepository;
import com.familyhub.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;

    @Transactional
    public TaskItem createTask(CreateTaskRequest request, CustomUserDetails currentUser) {
        User creator = userRepository.findById(currentUser.getId()).orElseThrow();

        TaskItem task = taskMapper.toEntity(request);
        task.setFamily(creator.getFamily());
        task.setCreatedBy(creator);

        // Jei nurodytas assignedToUserId — surasti vartotoją ir priskirti
        if (request.assignedToUserId() != null) {
            // Tik PARENT gali priskirti užduotį kitam žmogui
            if (currentUser.getRole() != Role.PARENT) {
                throw new AccessDeniedException();
            }
            User assignee = userRepository.findById(request.assignedToUserId()).orElseThrow();
            task.setAssignedTo(assignee);
        }

        return taskRepository.save(task);
    }

    @Transactional
    public TaskItem updateTask(Long taskId, UpdateTaskRequest request, CustomUserDetails currentUser) {
        TaskItem task = getTaskBelongingToFamily(taskId, currentUser.getFamilyId());

        // Tik PARENT gali redaguoti užduotis
        if (currentUser.getRole() != Role.PARENT) {
            throw new AccessDeniedException();
        }

        taskMapper.updateEntity(request, task);

        // Atnaujiname priskirtą vartotoją
        if (request.assignedToUserId() != null) {
            User assignee = userRepository.findById(request.assignedToUserId()).orElseThrow();
            task.setAssignedTo(assignee);
        } else {
            task.setAssignedTo(null);
        }

        return taskRepository.save(task);
    }

    @Transactional
    public void updateStatus(Long taskId, TaskStatus newStatus, CustomUserDetails currentUser) {
        TaskItem task = getTaskBelongingToFamily(taskId, currentUser.getFamilyId());

        // KID gali keisti statusą tik savo užduočių
        if (currentUser.getRole() == Role.KID) {
            boolean isAssignedToMe = task.getAssignedTo() != null
                    && task.getAssignedTo().getId().equals(currentUser.getId());
            if (!isAssignedToMe) {
                throw new AccessDeniedException();
            }
        }

        task.setStatus(newStatus);

        // Kai užduotis pažymima kaip atlikta — užfiksuojame laiką
        if (newStatus == TaskStatus.DONE) {
            task.setCompletedAt(LocalDateTime.now());
        } else {
            // Jei grąžinama atgal iš DONE — išvalome completedAt
            task.setCompletedAt(null);
        }

        taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long taskId, CustomUserDetails currentUser) {
        TaskItem task = getTaskBelongingToFamily(taskId, currentUser.getFamilyId());

        // Tik PARENT gali trinti užduotis
        if (currentUser.getRole() != Role.PARENT) {
            throw new AccessDeniedException();
        }

        taskRepository.delete(task);
    }

    @Transactional(readOnly = true)
    public List<TaskItem> getFamilyTasks(Long familyId) {
        return taskRepository.findAllByFamilyIdOrderByCreatedAtDesc(familyId);
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

    // Pagalbinis metodas: gauti užduotį ir patikrinti kad ji priklauso šiai šeimai.
    // Apsauga nuo URL manipuliavimo — vartotojas negali pasiekti kitų šeimų užduočių.
    private TaskItem getTaskBelongingToFamily(Long taskId, Long familyId) {
        TaskItem task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        if (!task.getFamily().getId().equals(familyId)) {
            throw new AccessDeniedException();
        }

        return task;
    }
}
