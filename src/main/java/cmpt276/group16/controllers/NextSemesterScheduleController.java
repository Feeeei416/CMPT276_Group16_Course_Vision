package cmpt276.group16.controllers;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import cmpt276.group16.models.StudentProfile;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;


@Controller
public class NextSemesterScheduleController {

    private final CurrentStudent currentStudent;

    public NextSemesterScheduleController(CurrentStudent currentStudent) {
        this.currentStudent = currentStudent;
    }

    @GetMapping("/student/schedule/next-semester")
    public String showSchedule(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return "student/next-semester-schedule";
    }
    
}