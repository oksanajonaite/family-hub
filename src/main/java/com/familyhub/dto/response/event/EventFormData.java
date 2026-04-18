package com.familyhub.dto.response.event;

import com.familyhub.entity.FamilyMember;
import com.familyhub.entity.Pet;
import com.familyhub.entity.User;
import com.familyhub.entity.enums.RecurrenceType;

import java.util.List;

// View model for the event create/edit form — bundles helper data needed by events/form.html.
// The bound form object (eventRequest) stays as a separate model attribute because
// Spring MVC's @ModelAttribute binding requires it at the model root level.
// eventId and participantIds are null on the create form, populated on the edit form.
public record EventFormData(
        List<User> registeredUsers,
        List<Pet> pets,
        List<FamilyMember> familyMembers,
        List<RecurrenceType> recurrenceTypes,
        Long eventId,
        List<String> participantIds
) {}
