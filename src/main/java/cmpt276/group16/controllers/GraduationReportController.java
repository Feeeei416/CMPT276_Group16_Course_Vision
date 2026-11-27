package cmpt276.group16.controllers;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import cmpt276.group16.graduation.GraduationReportService;
import cmpt276.group16.models.StudentProfile;
import jakarta.servlet.http.HttpSession;

// Graduation Report (#69): which of the student's degree requirements are completed, in progress, planned
// or still remaining. The student always comes from the session, so they only ever see their own report
@Controller
public class GraduationReportController {

    private final CurrentStudent currentStudent;
    private final GraduationReportService reportService;

    public GraduationReportController(CurrentStudent currentStudent, GraduationReportService reportService) {
        this.currentStudent = currentStudent;
        this.reportService = reportService;
    }

    @GetMapping("/student/graduation-report")
    public String report(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("student", student.get());
        // Null when the student's program and entry year have no requirements yet; the page says so
        model.addAttribute("report", reportService.forStudent(student.get()).orElse(null));
        return "student/graduation-report";
    }
}
