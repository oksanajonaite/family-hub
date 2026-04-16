package com.familyhub.dto.response;

import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.TaskPriority;

import java.util.List;

// View model for the task create/edit form — bundles helper data needed by tasks/form.html.
// The bound form object (taskRequest) stays as a separate model attribute because
// Spring MVC's @ModelAttribute binding requires it at the model root level.
// taskId and assigneeIds are null on the create form, populated on the edit form.
public record TaskFormData(
        List<User> registeredUsers,
        List<FamilyMember> familyMembers,
        List<TaskPriority> priorities,
        Long taskId,
        List<String> assigneeIds
) {}
