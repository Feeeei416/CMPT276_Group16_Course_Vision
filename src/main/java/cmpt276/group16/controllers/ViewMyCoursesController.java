package cmpt276.group16.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.services.CourseOptions;
import cmpt276.group16.services.Gpa;
import cmpt276.group16.services.UnitTotals;
import cmpt276.group16.transcript.CourseInput;
import cmpt276.group16.transcript.Terms;
import cmpt276.group16.transcript.TranscriptService;
import jakarta.servlet.http.HttpSession;

// My courses: the student's course record, with Add course and the Edit and Remove links.
// An assigned advisor gets the same page for a student under /advisor/students/{studentId}/courses (#78), so they
// can fix the record in an appointment. studentId is null on the student's own page. See PlanOwner
@Controller 
public class ViewMyCoursesController {

    static final String ADVISOR = "/advisor/students/{studentId}";

    private final PlanOwner.Finder owners;
    private final TranscriptCourseRepository transcriptCourseRepository;
    private final TranscriptService transcriptService;
    private final CourseOptions courseOptions;

    public ViewMyCoursesController(PlanOwner.Finder owners, TranscriptCourseRepository transcriptCourseRepository,
            TranscriptService transcriptService, CourseOptions courseOptions){
        this.owners = owners;
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.transcriptService = transcriptService;
        this.courseOptions = courseOptions;
    }

    @GetMapping({"/student/courses", ADVISOR + "/courses"})
    public String myCourses(@PathVariable(required = false) Long studentId, HttpSession session, Model model) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        return showMyCourses(model, owner.get(), Map.of(), List.of());
    }

    // The student comes from the session (or the advisor's assignment) and not from the form,
    // so a course can only be added to a record they're allowed to change
    @PostMapping({"/student/courses", ADVISOR + "/courses"})
    public String addCourse(@PathVariable(required = false) Long studentId, @RequestParam Map<String, String> form,
            HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }

        List<String> errors = CourseInput.validate(form);
        if (!errors.isEmpty()) {
            return showMyCourses(model, owner.get(), form, errors);
        }

        CourseInput course = CourseInput.from(form);
        if (!transcriptService.addByHand(owner.get().student(), course)) {
            return showMyCourses(model, owner.get(), form, List.of(CourseForms.alreadyInRecord(course, owner.get())));
        }

        redirectAttributes.addFlashAttribute("message",
                "Added " + CourseForms.name(course) + " to " + course.term() + ".");
        CourseForms.addUnitsNote(redirectAttributes, courseOptions, course);
        return "redirect:" + owner.get().basePath() + "/courses";
    }

    private String showMyCourses(Model model, PlanOwner owner, Map<String, String> form, List<String> errors) {
        StudentProfile student = owner.student();
        model.addAttribute("owner", owner);
        List<TranscriptCourse> courses = transcriptCourseRepository.findByStudentOrderByIdAsc(student);
        // Terms oldest first. courses within a term stay in the order they were saved
        Map<String, List<TranscriptCourse>> coursesByTerm = courses.stream().collect(
                Collectors.groupingBy(TranscriptCourse::getTerm, () -> new TreeMap<>(Terms.OLDEST_FIRST), Collectors.toList()));
        model.addAttribute("coursesByTerm", coursesByTerm);
        model.addAttribute("units", UnitTotals.of(courses));
        // Worked out on every visit, so adding, editing or removing a course updates it straight away
        model.addAttribute("gpa", Gpa.of(courses));
        CourseForms.addTo(model, form, errors, courseOptions.allCourses());
        return "student/view-courses";
    }
}