package cmpt276.group16.controllers;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourseRepository;
import jakarta.servlet.http.HttpSession;

@Controller
public class StudentHomeController {

    private final CurrentStudent currentStudent;
    private final TranscriptCourseRepository transcriptCourseRepository;

    public StudentHomeController(CurrentStudent currentStudent,
            TranscriptCourseRepository transcriptCourseRepository) {
        this.currentStudent = currentStudent;
        this.transcriptCourseRepository = transcriptCourseRepository;
    }

    @GetMapping("/student/home")
    public String home(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("displayName", student.get().getUser().getDisplayName());
        model.addAttribute("courses", transcriptCourseRepository.findByStudentOrderByIdAsc(student.get()));
        return "student/home";
    }
}
