package com.familyhub.controller;

import com.familyhub.dto.request.event.CreateEventRequest;
import com.familyhub.dto.request.event.UpdateEventRequest;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.enums.RecurrenceType;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.exception.EventNotFoundException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.EventService;
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
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public String listEvents(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        model.addAttribute("events", eventService.getVisibleFamilyEvents(currentUser.getFamilyId(), currentUser));
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
        model.addAttribute("formData", eventService.buildEventFormData(null, null, currentUser.getFamilyId()));
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
            model.addAttribute("formData", eventService.buildEventFormData(null, null, currentUser.getFamilyId()));
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
            UpdateEventRequest request = eventService.toEditRequest(event);

            model.addAttribute("eventRequest", request);
            model.addAttribute("formData", eventService.buildEventFormData(id, request.participantIds(), currentUser.getFamilyId()));
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
            model.addAttribute("formData", eventService.buildEventFormData(id, request.participantIds(), currentUser.getFamilyId()));
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
}
