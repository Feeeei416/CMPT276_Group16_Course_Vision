package cmpt276.group16.controllers;

import cmpt276.group16.models.*;
import cmpt276.group16.services.*;
import java.util.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller 
public class RegisterController {

    private final RegistrationService registrationService;

    public RegisterController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/register") 
    public String showRegisterPage(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        if (user != null){
            return LoginController.homeFor(user.getRole());
        }

        List<String>  errors = new ArrayList<>();
        Map<String, String>  form = new HashMap<>();
        return showRegisterForm(model, errors, form);
    }

    @PostMapping("/register")
    public String register(@RequestParam Map<String, String> form, Model model, HttpServletRequest request, HttpSession session) {
        List<String> errors = registrationService.validate(form);
        if (!errors.isEmpty()){
            return showRegisterForm(model, errors, form);
        }
        String newEmail = form.get("email");
        String newDisplayName = form.get("displayName");
        String newPassword = form.get("password");
        Program newProgram = RegistrationService.parseProgram(form.get("program"));
        Integer newCohortYear = RegistrationService.parseCohortYear(form.get("cohortYear"));

        User user;
        try {
            user = registrationService.createUserAndStudentProfile(newEmail, newDisplayName, newPassword, newProgram, newCohortYear);
        } catch (DataIntegrityViolationException e) {
            // Someone registered the same email between validate and save
            return showRegisterForm(model, List.of("An account with this email already exists."), form);
        }

        // Sign the new student in
        session.invalidate();
        request.getSession().setAttribute(LoginController.SESSION_USER, user);

        return LoginController.homeFor(user.getRole());
    }

    /** Shows the register page with the given errors and the typed values (passwords removed). */
    private String showRegisterForm(Model model, List<String> errors, Map<String, String> form) {
        // Copy the form so passwords are never sent back to the page
        Map<String, String> safeForm = new HashMap<>(form);
        safeForm.remove("password");
        safeForm.remove("confirmPassword");

        model.addAttribute("errors", errors);
        model.addAttribute("form", safeForm);
        model.addAttribute("programs", Program.values());
        model.addAttribute("cohortYears", RegistrationService.COHORT_YEARS);
        return "login/register";
    }
}
