package com.familyhub.controller;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.dto.request.task.UpdateTaskRequest;
import com.familyhub.dto.response.TaskFormData;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.FamilyMemberService;
import com.familyhub.service.FamilyService;
import com.familyhub.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final FamilyService familyService;
    private final FamilyMemberService familyMemberService;

    @GetMapping
    public String listTasks(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) TaskStatus status,
            Model model
    ) {
        var tasks = (status != null)
                ? taskService.getFamilyTasksByStatus(currentUser.getFamilyId(), status)
                : taskService.getFamilyTasks(currentUser.getFamilyId());

        model.addAttribute("tasks", tasks);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", TaskStatus.values());
        return "tasks/index";
    }

    @GetMapping("/create")
    public String createForm(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        model.addAttribute("taskRequest", new CreateTaskRequest(null, null, TaskPriority.MEDIUM, null, null));
        model.addAttribute("formData", buildFormData(null, null, currentUser));
        return "tasks/form";
    }

    @PostMapping("/create")
    public String createTask(
            @Valid @ModelAttribute("taskRequest") CreateTaskRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("formData", buildFormData(null, null, currentUser));
            return "tasks/form";
        }

        try {
            taskService.createTask(request, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Task created.");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks";
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can edit tasks.");
            return "redirect:/tasks";
        }

        TaskItem task;
        try {
            // getTaskByIdForFamily verifies the task belongs to the current user's family —
            // prevents cross-family access via direct URL manipulation
            task = taskService.getTaskByIdForFamily(id, currentUser.getFamilyId());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Task not found.");
            return "redirect:/tasks";
        }

        // Convert existing assignees into the prefixed string format expected by the form
        List<String> assigneeIds = new ArrayList<>();
        task.getAssignedUsers().forEach(u -> assigneeIds.add("USER_" + u.getId()));
        task.getAssignedMembers().forEach(m -> assigneeIds.add("MEMBER_" + m.getId()));

        UpdateTaskRequest request = new UpdateTaskRequest(
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                assigneeIds,
                task.getDueDate()
        );

        model.addAttribute("taskRequest", request);
        model.addAttribute("formData", buildFormData(id, assigneeIds, currentUser));
        return "tasks/form";
    }

    @PostMapping("/{id}/edit")
    public String updateTask(
            @PathVariable Long id,
            @Valid @ModelAttribute("taskRequest") UpdateTaskRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            // Pass assigneeIds from the submitted request so checkboxes stay checked on validation error
            model.addAttribute("formData", buildFormData(id, request.assigneeIds(), currentUser));
            return "tasks/form";
        }

        try {
            taskService.updateTask(id, request, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Task updated.");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            taskService.updateStatus(id, status, currentUser);
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks";
    }

    @PostMapping("/{id}/delete")
    public String deleteTask(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            taskService.deleteTask(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Task deleted.");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks";
    }

    // Builds the TaskFormData record for both create and edit forms.
    // taskId and assigneeIds are null on the create form, populated on the edit form.
    private TaskFormData buildFormData(Long taskId, List<String> assigneeIds, CustomUserDetails currentUser) {
        return new TaskFormData(
                familyService.getFamilyMembers(currentUser.getFamilyId()),
                familyMemberService.getFamilyMembers(currentUser.getFamilyId()),
                List.of(TaskPriority.values()),
                taskId,
                assigneeIds
        );
    }
}
