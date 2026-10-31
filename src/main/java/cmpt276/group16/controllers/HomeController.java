package cmpt276.group16.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import cmpt276.group16.models.*;
import jakarta.servlet.http.HttpSession;

/**
 * Shows the home page for each role.
 * AuthInterceptor runs first, so in these methods the user is always logged in
 * and has the role that matches the URL.
 */
@Controller 
public class HomeController {
    
    /** GET /student/home : home page for students. */
    @GetMapping("/student/home") 
    public String getStudentHome(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        model.addAttribute("user", user);
        return "student/home";
    }

    /** GET /advisor/home : home page for course advisors. */
    @GetMapping("/advisor/home") 
    public String getAdvisorHome(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        model.addAttribute("user", user);
        return "advisor/home";
    }

    /** GET /it/home : home page for IT managers. */
    @GetMapping("/it/home") 
    public String getITManagerHome(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        model.addAttribute("user", user);
        return "it/home";
    }
}
