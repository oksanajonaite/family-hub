package com.familyhub.controller;

import com.familyhub.dto.request.event.CreateEventRequest;
import com.familyhub.dto.request.event.UpdateEventRequest;
import com.familyhub.dto.response.event.EventResponse;
import com.familyhub.entity.enums.RecurrenceType;
import com.familyhub.exception.AccessDeniedException;
import com.familyhub.exception.EventNotFoundException;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.EventService;
import com.familyhub.service.FamilyService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final FamilyService familyService;

    // --- Eventų sąrašas ---
    // Grąžinami tik šeimos eventai, filtruojant privačius pagal matomumą.
    @GetMapping
    public String listEvents(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        List<EventResponse> events = eventService.getVisibleFamilyEvents(currentUser.getFamilyId(), currentUser);
        model.addAttribute("events", events);
        return "events/index";
    }

    // --- Naujo evento forma ---
    @GetMapping("/create")
    public String createForm(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        // Tuščias request su numatytomis reikšmėmis formai
        model.addAttribute("eventRequest", new CreateEventRequest(
                null, null, null, null, false, RecurrenceType.NONE, null, null, null
        ));
        model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
        model.addAttribute("recurrenceTypes", RecurrenceType.values());
        return "events/form";
    }

    // --- Naujo evento išsaugojimas ---
    @PostMapping("/create")
    public String createEvent(
            @Valid @ModelAttribute("eventRequest") CreateEventRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
            model.addAttribute("recurrenceTypes", RecurrenceType.values());
            return "events/form";
        }

        eventService.createEvent(request, currentUser);
        redirectAttributes.addFlashAttribute("successMessage", "Event created.");
        return "redirect:/events";
    }

    // --- Evento redagavimo forma ---
    @GetMapping("/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        try {
            EventResponse event = eventService.getEventById(id, currentUser);

            // Formai naudojame UpdateEventRequest su esamomis reikšmėmis
            UpdateEventRequest request = new UpdateEventRequest(
                    event.title(),
                    event.description(),
                    event.startsAt(),
                    event.endsAt(),
                    event.privateEvent(),
                    event.recurrenceType(),
                    event.recurrenceUntil(),
                    event.participantUserIds(),
                    event.participantPetIds()
            );

            model.addAttribute("eventRequest", request);
            model.addAttribute("eventId", id);
            model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
            model.addAttribute("recurrenceTypes", RecurrenceType.values());
            return "events/form";

        } catch (EventNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Event not found.");
            return "redirect:/events";
        }
    }

    // --- Evento atnaujinimas ---
    @PostMapping("/{id}/edit")
    public String updateEvent(
            @PathVariable Long id,
            @Valid @ModelAttribute("eventRequest") UpdateEventRequest request,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("eventId", id);
            model.addAttribute("members", familyService.getFamilyMembers(currentUser.getFamilyId()));
            model.addAttribute("recurrenceTypes", RecurrenceType.values());
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

    // --- Evento ištrynimas ---
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
