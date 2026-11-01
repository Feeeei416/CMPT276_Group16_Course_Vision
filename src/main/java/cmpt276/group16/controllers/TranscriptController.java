package cmpt276.group16.controllers;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.transcript.ParsedCourse;
import cmpt276.group16.transcript.TranscriptParseException;
import cmpt276.group16.transcript.TranscriptParseResult;
import cmpt276.group16.transcript.TranscriptParser;
import cmpt276.group16.transcript.TranscriptService;
import jakarta.servlet.http.HttpSession;

// Upload a transcript, review the courses found, then confirm to save them.
// The student's courses aren't changed until they confirm. Until then the parsed courses wait in the session,
// which Spring Session keeps in the database until it expires. The PDF itself is never stored
@Controller
@RequestMapping("/student/transcript")
public class TranscriptController {

    static final String PENDING_COURSES = "pendingCourses";

    private final CurrentStudent currentStudent;
    private final TranscriptParser transcriptParser;
    private final TranscriptService transcriptService;
    private final TranscriptCourseRepository transcriptCourseRepository;
    private final UploadLimit uploadLimit;

    public TranscriptController(CurrentStudent currentStudent, TranscriptParser transcriptParser,
            TranscriptService transcriptService, TranscriptCourseRepository transcriptCourseRepository,
            UploadLimit uploadLimit) {
        this.currentStudent = currentStudent;
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

    @GetMapping
    public String uploadPage(HttpSession session) {
        if (currentStudent.find(session).isEmpty()) {
            return "redirect:/login";
        }
        return "student/transcript-upload";
    }

    @PostMapping
    public String upload(@RequestParam(name = "file", required = false) MultipartFile file,
            HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        // Drop courses from any earlier upload that wasn't confirmed
        session.removeAttribute(PENDING_COURSES);

        if (file == null || file.isEmpty()) {
            model.addAttribute("error", "Please choose your transcript PDF to upload.");
            return "student/transcript-upload";
        }

        try {
            TranscriptParseResult result = transcriptParser.parse(file.getBytes());
            session.setAttribute(PENDING_COURSES, result.courses());
            model.addAttribute("courses", result.courses());
            model.addAttribute("skippedLines", result.skippedLines());
            // Confirming replaces these, so the review page warns about them
            model.addAttribute("savedCourseCount", transcriptCourseRepository.countByStudent(student.get()));
            return "student/transcript-review";
        } catch (TranscriptParseException e) {
            model.addAttribute("error", e.getMessage());
        } catch (IOException e) {
            model.addAttribute("error", "The upload didn't finish. Please try again.");
        }
        return "student/transcript-upload";
    }

    @PostMapping("/confirm")
    public String confirm(HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        // Nothing to confirm, e.g. the form was submitted twice
        if (!(session.getAttribute(PENDING_COURSES) instanceof List<?> pending)) {
            return "redirect:/student/transcript";
        }

        @SuppressWarnings("unchecked")
        List<ParsedCourse> courses = (List<ParsedCourse>) pending;
        transcriptService.replaceCourses(student.get(), courses);
        session.removeAttribute(PENDING_COURSES);

        redirectAttributes.addFlashAttribute("message",
                "Saved " + courses.size() + (courses.size() == 1 ? " course" : " courses") + " from your transcript.");
        return "redirect:/student/home";
    }
}
