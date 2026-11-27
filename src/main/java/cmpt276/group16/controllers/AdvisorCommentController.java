package cmpt276.group16.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.AdvisorCommentService;
import jakarta.servlet.http.HttpSession;

// #72: an advisor's comments on an assigned student's Graduation Report, written from the student's overview.
// The student reads them on their report. AuthInterceptor only lets advisors reach /advisor/**
@Controller
public class AdvisorCommentController {

    private final AssignedStudent assignedStudent;
    private final UserRepository userRepository;
    private final AdvisorCommentService commentService;

    public AdvisorCommentController(AssignedStudent assignedStudent, UserRepository userRepository,
            AdvisorCommentService commentService) {
        this.assignedStudent = assignedStudent;
        this.userRepository = userRepository;
        this.commentService = commentService;
    }

    /** POST /advisor/students/5/comments : adds a comment to student 5's report, then goes back to the overview. */
    @PostMapping("/advisor/students/{studentId}/comments")
    public String addComment(@PathVariable Long studentId, @RequestParam(defaultValue = "") String body,
            HttpSession session, RedirectAttributes redirectAttributes) {
        // A 403 unless the student is assigned to this advisor
        StudentProfile student = assignedStudent.find(session, studentId);
        List<String> problems = commentService.add(signedInAdvisor(session), student, body);
        if (!problems.isEmpty()) {
            redirectAttributes.addFlashAttribute("commentErrors", problems);
            // Put back in the text box, so a long comment isn't lost to a mistake
            redirectAttributes.addFlashAttribute("commentText", body);
        }
        return "redirect:/advisor/students/" + studentId + "#comments";
    }

    // AssignedStudent has already checked the signed-in user is an advisor, so this only fails if the account
    // was removed in between
    private User signedInAdvisor(HttpSession session) {
        return SessionUser.user(session, userRepository)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, AssignedStudent.NOT_ASSIGNED));
    }
}
