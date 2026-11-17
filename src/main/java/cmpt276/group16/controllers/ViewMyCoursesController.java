package cmpt276.group16.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.services.UnitTotals;
import cmpt276.group16.transcript.CourseInput;
import cmpt276.group16.transcript.Terms;
import cmpt276.group16.transcript.TranscriptService;
import jakarta.servlet.http.HttpSession;


@Controller 
public class ViewMyCoursesController {
    private final CurrentStudent currentStudent;
    private final TranscriptCourseRepository transcriptCourseRepository;
    private final TranscriptService transcriptService;

    public ViewMyCoursesController(CurrentStudent currentStudent, TranscriptCourseRepository transcriptCourseRepository,
            TranscriptService transcriptService){
        this.currentStudent = currentStudent;
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.transcriptService = transcriptService;
    }

    @GetMapping("/student/courses")
    public String myCourses(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return showMyCourses(model, student.get(), Map.of(), List.of());
    }

    // The student always comes from the session and not from the form, so they can only add to their own record
    @PostMapping("/student/courses")
    public String addCourse(@RequestParam Map<String, String> form, HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }

        List<String> errors = CourseInput.validate(form);
        if (!errors.isEmpty()) {
            return showMyCourses(model, student.get(), form, errors);
        }

        CourseInput course = CourseInput.from(form);
        if (!transcriptService.addByHand(student.get(), course)) {
            return showMyCourses(model, student.get(), form, List.of(CourseForms.alreadyInRecord(course)));
        }

        redirectAttributes.addFlashAttribute("message",
                "Added " + CourseForms.name(course) + " to " + course.term() + ".");
        return "redirect:/student/courses";
    }

    private String showMyCourses(Model model, StudentProfile student, Map<String, String> form, List<String> errors) {
        List<TranscriptCourse> courses = transcriptCourseRepository.findByStudentOrderByIdAsc(student);
        // Terms oldest first. courses within a term stay in the order they were saved
        Map<String, List<TranscriptCourse>> coursesByTerm = courses.stream().collect(
                Collectors.groupingBy(TranscriptCourse::getTerm, () -> new TreeMap<>(Terms.OLDEST_FIRST), Collectors.toList()));
        model.addAttribute("coursesByTerm", coursesByTerm);
        model.addAttribute("units", UnitTotals.of(courses));
        CourseForms.addTo(model, form, errors);
        return "student/view-courses";
    }
}