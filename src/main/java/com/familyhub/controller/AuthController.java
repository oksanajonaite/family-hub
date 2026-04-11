package com.familyhub.controller;

import com.familyhub.dto.request.auth.RegisterRequest;
import com.familyhub.exception.UserAlreadyExistsException;
import com.familyhub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// @Controller — ne @RestController. Metodai grąžina view pavadinimus (Thymeleaf),
// ne JSON. Spring MVC randa templates/auth/register.html ir jį renderina.
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // GET /login — tiesiog grąžina login formą.
    // Paties login POST nedarome — jį apdoroja Spring Security automatiškai.
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login"; // → templates/auth/login.html
    }

    // GET /register — paruošia tuščią formą.
    // Model — konteineris duomenims perduoti į Thymeleaf šabloną.
    @GetMapping("/register")
    public String registerPage(Model model) {
        // Tuščias objektas reikalingas Thymeleaf th:object="${registerRequest}".
        // Jei jo nebūtų — template mestų klaidą.
        model.addAttribute("registerRequest", new RegisterRequest("", "", ""));
        return "auth/register";
    }

    // POST /register — apdoroja formos duomenis
    @PostMapping("/register")
    public String register(
            // @Valid — paleidžia Bean Validation anotacijas iš RegisterRequest
            // (@NotBlank, @Email, @Size ir kt.)
            // @ModelAttribute — surišta su HTML formos laukais pagal vardą
            @Valid @ModelAttribute("registerRequest") RegisterRequest request,
            // BindingResult — talpina validacijos klaidas.
            // SVARBU: turi eiti iš karto po @Valid parametro, kitaip Spring mets exception.
            BindingResult bindingResult,
            // RedirectAttributes — perduoda duomenis po redirect (flash scope).
            // Normali Model dingsta po redirect — flash išlieka vieną request'ą.
            RedirectAttributes redirectAttributes
    ) {
        // Jei yra validacijos klaidų (pvz. per trumpas slaptažodis) —
        // grąžiname tą patį puslapį. BindingResult klaidos automatiškai
        // rodomos Thymeleaf th:errors="*{laukas}" vietose.
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            authService.register(request);
            // addFlashAttribute — žinutė išliks tik vienam sekančiam request'ui.
            // Po redirect į /login, login.html parodys šią žinutę ir ji dings.
            redirectAttributes.addFlashAttribute("successMessage", "Account created! Please log in.");
            return "redirect:/login"; // HTTP 302 redirect į /login
        } catch (UserAlreadyExistsException e) {
            // rejectValue — prideda klaidą prie konkretaus lauko.
            // "email" — lauko pavadinimas, "error.email" — klaidos kodas, paskutinis — žinutė.
            // Thymeleaf rodys šią klaidą prie email input lauko.
            bindingResult.rejectValue("email", "error.email", "This email is already registered.");
            return "auth/register";
        }
    }
}
