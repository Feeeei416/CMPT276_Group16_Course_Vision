package cmpt276.group16.controllers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.services.CourseOptions;
import cmpt276.group16.transcript.CourseInput;
import cmpt276.group16.transcript.TranscriptService;
import jakarta.servlet.http.HttpSession;

// Edit and Remove for one course in the signed-in student's record, or an assigned student's record for their
// advisor under /advisor/students/{studentId}/courses (#78). studentId is null on the student's own pages.
// Every lookup uses the id and the student together, so another student's course id is treated as missing
@Controller
public class EditCourseController {

    private static final String STUDENT = "/student/courses/{id}";
    private static final String ADVISOR = ViewMyCoursesController.ADVISOR + "/courses/{id}";

    // Same message whether the course doesn't exist or belongs to someone else, so ids can't be probed
    static final String NOT_FOUND = "That course isn't in your record.";
    static final String NOT_FOUND_FOR_ADVISOR = "That course isn't in their record.";

    private final PlanOwner.Finder owners;
    private final TranscriptCourseRepository transcriptCourseRepository;
    private final TranscriptService transcriptService;
    private final CourseOptions courseOptions;

    public EditCourseController(PlanOwner.Finder owners, TranscriptCourseRepository transcriptCourseRepository,
            TranscriptService transcriptService, CourseOptions courseOptions) {
        this.owners = owners;
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.transcriptService = transcriptService;
        this.courseOptions = courseOptions;
    }

    @GetMapping({STUDENT + "/edit", ADVISOR + "/edit"})
    public String showEditForm(@PathVariable(required = false) Long studentId, @PathVariable Long id,
            HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        Optional<TranscriptCourse> course = transcriptCourseRepository.findByIdAndStudent(id, owner.get().student());
        if (course.isEmpty()) {
            return notFound(owner.get(), redirectAttributes);
        }
        return showEditPage(model, owner.get(), course.get(), CourseInput.formFor(course.get()), List.of());
    }

    @PostMapping({STUDENT + "/edit", ADVISOR + "/edit"})
    public String saveEdit(@PathVariable(required = false) Long studentId, @PathVariable Long id,
            @RequestParam Map<String, String> form, HttpSession session, Model model,
            RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        Optional<TranscriptCourse> course = transcriptCourseRepository.findByIdAndStudent(id, owner.get().student());
        if (course.isEmpty()) {
            return notFound(owner.get(), redirectAttributes);
        }

        // Same checks as adding a course; nothing changes unless they all pass.
        // The units it has now are always allowed, since uploaded courses aren't capped
        List<String> errors = CourseInput.validate(form, course.get().getUnits());
        if (!errors.isEmpty()) {
            return showEditPage(model, owner.get(), course.get(), form, errors);
        }

        CourseInput edited = CourseInput.from(form, course.get().getUnits());
        if (!transcriptService.editByHand(course.get(), edited)) {
            return showEditPage(model, owner.get(), course.get(), form,
                    List.of(CourseForms.alreadyInRecord(edited, owner.get())));
        }

        redirectAttributes.addFlashAttribute("message", "Saved " + CourseForms.name(edited) + ".");
        CourseForms.addUnitsNote(redirectAttributes, courseOptions, edited);
        return "redirect:" + owner.get().basePath() + "/courses";
    }

    // The page asks to confirm before this form is submitted
    @PostMapping({STUDENT + "/remove", ADVISOR + "/remove"})
    public String remove(@PathVariable(required = false) Long studentId, @PathVariable Long id, HttpSession session,
            RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        Optional<TranscriptCourse> course = transcriptCourseRepository.findByIdAndStudent(id, owner.get().student());
        if (course.isEmpty()) {
            return notFound(owner.get(), redirectAttributes);
        }

        transcriptCourseRepository.delete(course.get());
        redirectAttributes.addFlashAttribute("message", "Removed " + course.get().getSubject() + " "
                + course.get().getNumber() + " from " + (owner.get().byAdvisor() ? "their" : "your") + " record.");
        return "redirect:" + owner.get().basePath() + "/courses";
    }

    private String showEditPage(Model model, PlanOwner owner, TranscriptCourse course, Map<String, String> form,
            List<String> errors) {
        model.addAttribute("owner", owner);
        model.addAttribute("courseId", course.getId());
        CourseForms.addTo(model, form, errors, courseOptions.allCourses(), course.getUnits());
        return "student/edit-course";
    }

    private String notFound(PlanOwner owner, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", owner.byAdvisor() ? NOT_FOUND_FOR_ADVISOR : NOT_FOUND);
        return "redirect:" + owner.basePath() + "/courses";
    }
}
