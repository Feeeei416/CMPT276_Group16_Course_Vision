package cmpt276.group16.controllers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import jakarta.servlet.http.HttpSession;


@Controller 
public class ViewMyCoursesController {
    private final CurrentStudent currentStudent;
    private final TranscriptCourseRepository transcriptCourseRepository;

    public ViewMyCoursesController(CurrentStudent currentStudent, TranscriptCourseRepository transcriptCourseRepository){
        this.currentStudent = currentStudent;
        this.transcriptCourseRepository = transcriptCourseRepository;
    }

    @GetMapping("/student/courses")
    public String myCourses(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        List<TranscriptCourse> courses = transcriptCourseRepository.findByStudentOrderByIdAsc(student.get());
        Map<String, List<TranscriptCourse>> coursesByTerm = courses.stream().collect(Collectors.groupingBy(TranscriptCourse::getTerm, LinkedHashMap::new, Collectors.toList()));
        model.addAttribute("coursesByTerm", coursesByTerm);
        return "view-my-courses";
    }
}

