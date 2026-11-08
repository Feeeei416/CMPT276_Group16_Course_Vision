package cmpt276.group16.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.transcript.CourseInput;
import jakarta.servlet.http.HttpSession;

// Edit and Remove for one course in the signed-in student's record
// Every lookup uses the id and the student together, so another student's course id is treated as missing
@Controller
@RequestMapping("/student/courses/{id}")
public class EditCourseController {

    // Same message whether the course doesn't exist or belongs to someone else, so ids can't be probed
    static final String NOT_FOUND = "That course isn't in your record.";

    private final CurrentStudent currentStudent;
    private final TranscriptCourseRepository transcriptCourseRepository;

    public EditCourseController(CurrentStudent currentStudent, TranscriptCourseRepository transcriptCourseRepository) {
        this.currentStudent = currentStudent;
        this.transcriptCourseRepository = transcriptCourseRepository;
    }

    @GetMapping("/edit")
    public String showEditForm(@PathVariable Long id, HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        Optional<TranscriptCourse> course = transcriptCourseRepository.findByIdAndStudent(id, student.get());
        if (course.isEmpty()) {
            return notFound(redirectAttributes);
        }
        return showEditPage(model, course.get(), CourseInput.formFor(course.get()), List.of());
    }

    @PostMapping("/edit")
    public String saveEdit(@PathVariable Long id, @RequestParam Map<String, String> form, HttpSession session,
            Model model, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        Optional<TranscriptCourse> course = transcriptCourseRepository.findByIdAndStudent(id, student.get());
        if (course.isEmpty()) {
            return notFound(redirectAttributes);
        }

        // Same checks as adding a course; nothing changes unless they all pass
        List<String> errors = CourseInput.validate(form);
        if (!errors.isEmpty()) {
            return showEditPage(model, course.get(), form, errors);
        }

        CourseInput edited = CourseInput.from(form);
        course.get().editByHand(edited.term(), edited.subject(), edited.number(), edited.title(), edited.units(),
                edited.grade());
        transcriptCourseRepository.save(course.get());

        redirectAttributes.addFlashAttribute("message", "Saved " + CourseForms.name(edited) + ".");
        return "redirect:/student/courses";
    }

    // The page asks the student to confirm before this form is submitted
    @PostMapping("/remove")
    public String remove(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        Optional<TranscriptCourse> course = transcriptCourseRepository.findByIdAndStudent(id, student.get());
        if (course.isEmpty()) {
            return notFound(redirectAttributes);
        }

        transcriptCourseRepository.delete(course.get());
        redirectAttributes.addFlashAttribute("message",
                "Removed " + course.get().getSubject() + " " + course.get().getNumber() + " from your record.");
        return "redirect:/student/courses";
    }

    private String showEditPage(Model model, TranscriptCourse course, Map<String, String> form, List<String> errors) {
        model.addAttribute("courseId", course.getId());
        CourseForms.addTo(model, form, errors);
        return "student/edit-course";
    }

    private String notFound(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", NOT_FOUND);
        return "redirect:/student/courses";
    }
}
