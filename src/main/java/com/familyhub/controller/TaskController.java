package com.familyhub.controller;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.dto.request.task.UpdateTaskRequest;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.security.CustomUserDetails;
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

@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final FamilyService familyService;

    @GetMapping
    public String listTasks(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) TaskStatus status,
            Model model
    ) {
        // Filtravimas pagal statusą — jei status nenurodytas, rodome visas
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
        model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
        model.addAttribute("priorities", TaskPriority.values());
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
            model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
            model.addAttribute("priorities", TaskPriority.values());
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

        TaskItem task = taskService.getTaskById(id);
        // Formai naudojame UpdateTaskRequest — užpildom esamomis reikšmėmis
        UpdateTaskRequest request = new UpdateTaskRequest(
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getAssignedTo() != null ? task.getAssignedTo().getId() : null,
                task.getDueDate()
        );

        model.addAttribute("taskRequest", request);
        model.addAttribute("taskId", id);
        model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
        model.addAttribute("priorities", TaskPriority.values());
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
            model.addAttribute("taskId", id);
            model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
            model.addAttribute("priorities", TaskPriority.values());
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
}
