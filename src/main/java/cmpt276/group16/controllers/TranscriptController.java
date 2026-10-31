package cmpt276.group16.controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.transcript.ParsedCourse;
import cmpt276.group16.transcript.TranscriptParseException;
import cmpt276.group16.transcript.TranscriptParseResult;
import cmpt276.group16.transcript.TranscriptParser;
import cmpt276.group16.transcript.TranscriptService;
import jakarta.servlet.http.HttpSession;

// Upload a transcript, review the courses found, then confirm to save them.
// Nothing is saved until the confirm, the parsed courses wait in the session, and the PDF is never stored
@Controller
@RequestMapping("/student/transcript")
public class TranscriptController {

	static final String PENDING_COURSES = "pendingCourses";

	private final CurrentStudent currentStudent;
	private final TranscriptParser transcriptParser;
	private final TranscriptService transcriptService;

	public TranscriptController(CurrentStudent currentStudent, TranscriptParser transcriptParser,
			TranscriptService transcriptService) {
		this.currentStudent = currentStudent;
		this.transcriptParser = transcriptParser;
		this.transcriptService = transcriptService;
	}

	@GetMapping
	public String uploadPage(HttpSession session) {
		if (currentStudent.find(session).isEmpty()) {
			return "redirect:/login";
		}
		return "transcript-upload";
	}

	@PostMapping
	public String upload(@RequestParam(name = "file", required = false) MultipartFile file,
			HttpSession session, Model model) {
		if (currentStudent.find(session).isEmpty()) {
			return "redirect:/login";
		}
		// Drop courses from any earlier upload that wasn't confirmed
		session.removeAttribute(PENDING_COURSES);

		if (file == null || file.isEmpty()) {
			model.addAttribute("error", "Please choose your transcript PDF to upload.");
			return "transcript-upload";
		}

		try {
			TranscriptParseResult result = transcriptParser.parse(file.getBytes());
			session.setAttribute(PENDING_COURSES, new ArrayList<>(result.courses()));
			model.addAttribute("courses", result.courses());
			model.addAttribute("skippedLines", result.skippedLines());
			return "transcript-review";
		} catch (TranscriptParseException e) {
			model.addAttribute("error", e.getMessage());
		} catch (IOException e) {
			model.addAttribute("error", "The upload didn't finish. Please try again.");
		}
		return "transcript-upload";
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

		redirectAttributes.addFlashAttribute("message", "Saved " + courses.size() + " courses from your transcript.");
		return "redirect:/student/home";
	}
}
