package cmpt276.group16.controllers;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.services.EligibilityService;
import jakarta.servlet.http.HttpSession;

// Eligible courses: what a student can take next semester (#38), and why they can't take the rest (#39).
// The student always comes from the session, so they only ever see results for their own record
@Controller
public class EligibleCoursesController {

    private final CurrentStudent currentStudent;
    private final EligibilityService eligibilityService;

    public EligibleCoursesController(CurrentStudent currentStudent, EligibilityService eligibilityService) {
        this.currentStudent = currentStudent;
        this.eligibilityService = eligibilityService;
    }

    @GetMapping("/student/eligible")
    public String eligibleCourses(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("eligibility", eligibilityService.forNextSemester(student.get()));
        return "student/eligible";
    }
}