package cmpt276.group16.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.transcript.ParsedCourse;
import cmpt276.group16.transcript.TranscriptParseException;
import cmpt276.group16.transcript.TranscriptParseResult;
import cmpt276.group16.transcript.TranscriptParser;
import cmpt276.group16.transcript.TranscriptService;
import jakarta.servlet.http.HttpSession;

// Upload a transcript, review the courses found, then confirm to save them.
// The student's courses aren't changed until they confirm. Until then the parsed courses wait in the session,
// which Spring Session keeps in the database until it expires. The PDF itself is never stored.
// An assigned advisor gets the same pages for a student under /advisor/students/{studentId}/transcript (#78),
// for a student who brings their transcript to an appointment. studentId is null on the student's own pages
@Controller
public class TranscriptController {

    private static final String STUDENT = "/student/transcript";
    private static final String ADVISOR = ViewMyCoursesController.ADVISOR + "/transcript";

    static final String PENDING_COURSES = "pendingCourses";

    private final PlanOwner.Finder owners;
    private final TranscriptParser transcriptParser;
    private final TranscriptService transcriptService;
    private final TranscriptCourseRepository transcriptCourseRepository;
    private final UploadLimit uploadLimit;

    public TranscriptController(PlanOwner.Finder owners, TranscriptParser transcriptParser,
            TranscriptService transcriptService, TranscriptCourseRepository transcriptCourseRepository,
            UploadLimit uploadLimit) {
        this.owners = owners;
        this.transcriptParser = transcriptParser;
        this.transcriptService = transcriptService;
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.uploadLimit = uploadLimit;
    }

    // The upload page checks the file size in the browser before sending it
    @ModelAttribute("uploadLimit")
    UploadLimit uploadLimit() {
        return uploadLimit;
    }

    // The parsed courses wait under a key for the student they're for. An advisor with two students open in two
    // tabs could otherwise upload one student's transcript and confirm it into the other student's record
    static String pendingKey(PlanOwner owner) {
        return owner.byAdvisor() ? PENDING_COURSES + ":" + owner.student().getUserId() : PENDING_COURSES;
    }

    @GetMapping({STUDENT, ADVISOR})
    public String uploadPage(@PathVariable(required = false) Long studentId, HttpSession session, Model model) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("owner", owner.get());
        return "student/transcript-upload";
    }

    @PostMapping({STUDENT, ADVISOR})
    public String upload(@PathVariable(required = false) Long studentId,
            @RequestParam(name = "file", required = false) MultipartFile file, HttpSession session, Model model) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("owner", owner.get());
        // Drop courses from any earlier upload for this student that wasn't confirmed
        session.removeAttribute(pendingKey(owner.get()));

        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "Please choose " + (owner.get().byAdvisor() ? "their" : "your")
                    + " transcript PDF to upload.");
            return "student/transcript-upload";
        }

        try {
            TranscriptParseResult result = transcriptParser.parse(file.getBytes());
            session.setAttribute(pendingKey(owner.get()), result.courses());
            model.addAttribute("courses", result.courses());
            model.addAttribute("skippedLines", result.skippedLines());
            // Confirming replaces the courses from the last transcript, so the review page warns about them
            // Courses added or edited by hand are kept, so they aren't counted
            model.addAttribute("transcriptCourseCount", transcriptCourseRepository.countByStudentAndSource(
                    owner.get().student(), TranscriptCourse.Source.TRANSCRIPT));
            return "student/transcript-review";
        } catch (TranscriptParseException e) {
            model.addAttribute("error", e.getMessage());
        } catch (IOException e) {
            model.addAttribute("error", "The upload didn't finish. Please try again.");
        }
        return "student/transcript-upload";
    }

    @PostMapping({STUDENT + "/confirm", ADVISOR + "/confirm"})
    public String confirm(@PathVariable(required = false) Long studentId, HttpSession session,
            RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        // Nothing to confirm for this student, e.g. the form was submitted twice
        if (!(session.getAttribute(pendingKey(owner.get())) instanceof List<?> pending)) {
            return "redirect:" + owner.get().basePath() + "/transcript";
        }

        @SuppressWarnings("unchecked")
        List<ParsedCourse> courses = (List<ParsedCourse>) pending;
        transcriptService.replaceCourses(owner.get().student(), courses);
        session.removeAttribute(pendingKey(owner.get()));

        redirectAttributes.addFlashAttribute("message", "Saved " + courses.size()
                + (courses.size() == 1 ? " course" : " courses") + " from " + whose(owner.get()) + " transcript.");
        return "redirect:" + owner.get().homePath();
    }

    // Throws the parsed courses away, so going back to the review page and confirming saves nothing
    @PostMapping({STUDENT + "/cancel", ADVISOR + "/cancel"})
    public String cancel(@PathVariable(required = false) Long studentId, HttpSession session,
            RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = owners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        session.removeAttribute(pendingKey(owner.get()));

        redirectAttributes.addFlashAttribute("message",
                "Nothing was saved from " + (owner.get().byAdvisor() ? "their" : "your") + " transcript.");
        return "redirect:" + owner.get().homePath();
    }

    // "your" for the student, "Madden's" for their advisor
    private static String whose(PlanOwner owner) {
        return owner.byAdvisor() ? owner.name() + "'s" : "your";
    }
}
