package com.familyhub.controller;

import com.familyhub.dto.request.event.CreateEventRequest;
import com.familyhub.dto.request.event.UpdateEventRequest;
import com.familyhub.dto.response.EventFormData;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.enums.RecurrenceType;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.exception.EventNotFoundException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.EventService;
import com.familyhub.service.FamilyMemberService;
import com.familyhub.service.FamilyService;
import com.familyhub.service.PetService;
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
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final FamilyService familyService;
    private final FamilyMemberService familyMemberService;
    private final PetService petService;

    @GetMapping
    public String listEvents(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        List<EventResponse> events = eventService.getVisibleFamilyEvents(currentUser.getFamilyId(), currentUser);
        model.addAttribute("events", events);
        return "events/index";
    }

    @GetMapping("/create")
    public String createForm(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            Model model
    ) {
        model.addAttribute("eventRequest", new CreateEventRequest(
                null, null, null, null, null, null, false, RecurrenceType.NONE, null, null
        ));
        model.addAttribute("formData", buildFormData(null, null, currentUser));
        NavigationUtils.applyBackNavigation(model, from, "/events", "Back to events");
        return "events/form";
    }

    @PostMapping("/create")
    public String createEvent(
            @Valid @ModelAttribute("eventRequest") CreateEventRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("formData", buildFormData(null, null, currentUser));
            NavigationUtils.applyBackNavigation(model, from, "/events", "Back to events");
            return "events/form";
        }

        eventService.createEvent(request, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Event created.");
        return "redirect:/events";
    }

    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            EventResponse event = eventService.getEventById(id, currentUser);

            // Convert existing participants into the prefixed string format expected by the form
            List<String> participantIds = new ArrayList<>();
            event.participantUserIds().forEach(uid -> participantIds.add("USER_" + uid));
            event.participantPetIds().forEach(pid -> participantIds.add("PET_" + pid));
            event.participantFamilyMemberIds().forEach(mid -> participantIds.add("MEMBER_" + mid));

            // Split existing LocalDateTime back into separate date + time for the form inputs
            UpdateEventRequest request = new UpdateEventRequest(
                    event.title(),
                    event.description(),
                    event.startsAt().toLocalDate(),
                    event.startsAt().toLocalTime(),
                    event.endsAt() != null ? event.endsAt().toLocalDate() : null,
                    event.endsAt() != null ? event.endsAt().toLocalTime() : null,
                    event.privateEvent(),
                    event.recurrenceType(),
                    event.recurrenceUntil(),
                    participantIds
            );

            model.addAttribute("eventRequest", request);
            model.addAttribute("formData", buildFormData(id, participantIds, currentUser));
            NavigationUtils.applyBackNavigation(model, from, "/events", "Back to events");
            return "events/form";

        } catch (EventNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Event not found.");
            return "redirect:/events";
        }
    }

    @PostMapping("/{id}/edit")
    public String updateEvent(
            @PathVariable Long id,
            @Valid @ModelAttribute("eventRequest") UpdateEventRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String from,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            // Pass participantIds from the submitted request so checkboxes stay checked on validation error
            model.addAttribute("formData", buildFormData(id, request.participantIds(), currentUser));
            NavigationUtils.applyBackNavigation(model, from, "/events", "Back to events");
            return "events/form";
        }

        try {
            eventService.updateEvent(id, request, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Event updated.");
        } catch (AccessDeniedException | EventNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/events";
    }

    @PostMapping("/{id}/delete")
    public String deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            RedirectAttributes redirectAttributes
    ) {
        try {
            eventService.deleteEvent(id, currentUser);
            redirectAttributes.addFlashAttribute("successMessage", "Event deleted.");
        } catch (AccessDeniedException | EventNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/events";
    }

    // Builds the EventFormData record for both create and edit forms.
    // eventId and participantIds are null on the create form, populated on the edit form.
    private EventFormData buildFormData(Long eventId, List<String> participantIds, CustomUserDetails currentUser) {
        return new EventFormData(
                familyService.getFamilyUsers(currentUser.getFamilyId()),
                petService.getFamilyPets(currentUser.getFamilyId()),
                familyMemberService.getFamilyMembers(currentUser.getFamilyId()),
                List.of(RecurrenceType.values()),
                eventId,
                participantIds
        );
    }

}
