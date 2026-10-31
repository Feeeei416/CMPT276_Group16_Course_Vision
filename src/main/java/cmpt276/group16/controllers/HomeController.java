package cmpt276.group16.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import cmpt276.group16.models.*;
import jakarta.servlet.http.HttpSession;

@Controller 
public class HomeController {
    @GetMapping("/student/home") 
    public String getStudentHome(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        if (user == null){
            return "redirect:/login";
        }
        else{
            model.addAttribute("user", user);
            return "student/home";
        }
    }

    @GetMapping("/advisor/home") 
    public String getAdvisorHome(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        if (user == null){
            return "redirect:/login";
        }
        else{
            model.addAttribute("user", user);
            return "advisor/home";
        }
    }

    @GetMapping("/it/home") 
    public String getITManagerHome(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        if (user == null){
            return "redirect:/login";
        }
        else{
            model.addAttribute("user", user);
            return "it/home";
        }
    }
}
