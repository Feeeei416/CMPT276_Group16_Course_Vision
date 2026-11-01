package cmpt276.group16.controllers;

import java.util.*;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import cmpt276.group16.models.*;
import jakarta.servlet.http.HttpSession;

@Controller 
public class RegisterController {
    
    private static final List<Integer> COHORT_YEARS = List.of(2023, 2024);

    @GetMapping("/register") 
    public String showRegisterPage(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        if (user != null){
            return LoginController.homeFor(user.getRole());
        }

        List<String>  errors = new ArrayList<>();
        Map<String, String>  form = new HashMap<>();
        model.addAttribute("errors", errors);
        model.addAttribute("form", form);
        model.addAttribute("programs", Program.values());
        model.addAttribute("cohortYears", COHORT_YEARS);
        return "login/register";
    }
}
