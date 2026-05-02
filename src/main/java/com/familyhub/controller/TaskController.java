package com.familyhub.controller;

import com.familyhub.dto.request.task.CreateTaskRequest;
import com.familyhub.dto.request.task.UpdateTaskRequest;
import com.familyhub.entity.TaskItem;
import com.familyhub.entity.enums.Role;
import com.familyhub.entity.enums.TaskPriority;
import com.familyhub.entity.enums.TaskStatus;
import com.familyhub.exception.ForbiddenException;
import com.familyhub.exception.TaskNotFoundException;
import com.familyhub.security.CustomUserDetails;
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

import java.util.List;

/**
 * CRUD for family tasks with status tracking (TODO / IN_PROGRESS / DONE).
 * Create and edit are restricted to PARENT role; KIDs can update status on their assigned tasks.
 * Supports optional back-navigation via the {@code from} query parameter.
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public String listTasks(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) TaskStatus status,
            Model model
    ) {
        List<TaskItem> allTasks = taskService.getFamilyTasks(currentUser.getFamilyId(), currentUser);
        List<TaskItem> tasks = (status != null)
                ? allTasks.stream().filter(t -> t.getStatus() == status).toList()
                : allTasks;

        model.addAttribute("tasks", tasks);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", TaskStatus.values());
        model.addAttribute("totalCount", allTasks.size());
        model.addAttribute("todoCount", allTasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).count());
        model.addAttribute("inProgressCount", allTasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count());
        model.addAttribute("doneCount", allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count());
        return "tasks/index";
    }

    // Role guard: only PARENT can open the task creation form.
    // The UI already hides the "New Task" button from KID (sec:authorize in Thymeleaf),
    // but this check is the real defence — anyone can type the URL directly in the browser.
    @GetMapping("/create")
    public String createForm(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can create tasks.");
            return "redirect:/tasks";
        }
        model.addAttribute("taskRequest", new CreateTaskRequest(null, null, TaskPriority.MEDIUM, null, null, false));
        model.addAttribute("formData", taskService.buildTaskFormData(null, null, currentUser.getFamilyId()));
        NavigationUtils.applyBackNavigation(model, from, "/tasks", "Back to tasks");
        return "tasks/form";
    }

    // Role guard repeated on the POST endpoint — a KID could craft a raw HTTP POST request
    // even if they never saw the form. Both GET and POST must be protected independently.
    @PostMapping("/create")
    public String createTask(
            @Valid @ModelAttribute("taskRequest") CreateTaskRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            @RequestParam(required = false, defaultValue = "save") String submitAction,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can create tasks.");
            return "redirect:/tasks";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("formData", taskService.buildTaskFormData(null, null, currentUser.getFamilyId()));
            NavigationUtils.applyBackNavigation(model, from, "/tasks", "Back to tasks");
            return "tasks/form";
        }

        try {
            TaskItem savedTask = taskService.createTask(request, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Task created.");
            if ("edit".equals(submitAction)) {
                if ("dashboard".equals(from)) {
                    return "redirect:/tasks/" + savedTask.getId() + "/edit?from=dashboard";
                }
                return "redirect:/tasks/" + savedTask.getId() + "/edit";
            }
            if ("dashboard".equals(from)) {
                return "redirect:/dashboard";
            }
            return "redirect:/tasks";
        } catch (ForbiddenException e) {
            model.addAttribute("formData", taskService.buildTaskFormData(null, request.assigneeIds(), currentUser.getFamilyId()));
            NavigationUtils.applyBackNavigation(model, from, "/tasks", "Back to tasks");
            model.addAttribute("errorMessage", e.getMessage());
            return "tasks/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (currentUser.getRole() != Role.PARENT) {
            redirectAttributes.addFlashAttribute("errorMessage", "Only parents can edit tasks.");
            return "redirect:/tasks";
        }

        try {
            TaskItem task = taskService.getTaskByIdForFamily(id, currentUser.getFamilyId());
            UpdateTaskRequest request = taskService.toEditRequest(task);
            model.addAttribute("taskRequest", request);
            model.addAttribute("formData", taskService.buildTaskFormData(id, request.assigneeIds(), currentUser.getFamilyId()));
            NavigationUtils.applyBackNavigation(model, from, "/tasks", "Back to tasks");
            return "tasks/form";
        } catch (TaskNotFoundException | ForbiddenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Task not found.");
            return "redirect:/tasks";
        }
    }

    @GetMapping("/{id}")
    public String taskDetails(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            TaskItem task = taskService.getTaskByIdForFamily(id, currentUser.getFamilyId());
            model.addAttribute("task", task);
            model.addAttribute("statuses", TaskStatus.values());
            NavigationUtils.applyBackNavigation(model, from, "/tasks?status=TODO", "Back to tasks");
            model.addAttribute("fromDashboard", "dashboard".equals(from));
            return "tasks/detail";
        } catch (TaskNotFoundException | ForbiddenException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Task not found.");
            return "redirect:/tasks";
        }
    }

    @PostMapping("/{id}/edit")
    public String updateTask(
            @PathVariable Long id,
            @Valid @ModelAttribute("taskRequest") UpdateTaskRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("formData", taskService.buildTaskFormData(id, request.assigneeIds(), currentUser.getFamilyId()));
            NavigationUtils.applyBackNavigation(model, from, "/tasks", "Back to tasks");
            return "tasks/form";
        }

        try {
            taskService.updateTask(id, request, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Task updated.");
        } catch (ForbiddenException | TaskNotFoundException e) {
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
        } catch (ForbiddenException e) {
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
        } catch (ForbiddenException | TaskNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks";
    }
}
