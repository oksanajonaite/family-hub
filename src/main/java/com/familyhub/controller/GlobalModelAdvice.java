package com.familyhub.controller;

import com.familyhub.entity.enums.Role;
import com.familyhub.security.CustomUserDetails;
import com.familyhub.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;

// assignableTypes — advice veikia TIK išvardintiems controller'iams.
// AuthController čia NĖRA — login/register puslapiuose šis advice visai nevykdomas.
// Tai švariau nei globalus @ControllerAdvice, nes aiškiai matyti kas naudoja šį advice.
@ControllerAdvice(assignableTypes = {
        DashboardController.class,
        TaskController.class,
        EventController.class,
        PetController.class,
        FamilyMemberController.class,
        FamilyController.class,
        NotificationController.class,
        AdminController.class
})
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationService notificationService;

    // @ModelAttribute ant metodo — automatiškai prideda reikšmes į modelį
    // prieš kiekvieną iš aukščiau išvardintų controller'ių metodą.
    @ModelAttribute
    public void addGlobalAttributes(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            Model model
    ) {
        // Neperskaitytų pranešimų skaičius — tik PARENT/KID vartotojams.
        // ADMIN neturi šeimos ir pranešimų, todėl jo neįtraukiame.
        if (currentUser != null && currentUser.getRole() != Role.ADMIN) {
            long unreadCount = notificationService.countUnread(currentUser);
            model.addAttribute("unreadCount", unreadCount);
        }

        // Šiandienos data kaip tekstas "yyyy-MM-dd" — naudojama HTML datos laukų
        // max atribute (pvz. gimimo data negali būti ateityje).
        model.addAttribute("today", LocalDate.now().toString());
    }
}
