package cmpt276.group16.controllers;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

// Files over spring.servlet.multipart.max-file-size are rejected before they reach TranscriptController,
// so the error is caught here instead
@ControllerAdvice
public class UploadErrorHandler {

    private final UploadLimit uploadLimit;

    // "/advisor/students/5/transcript": an advisor's upload for student 5 (#78)
    private static final Pattern ADVISOR_UPLOAD = Pattern.compile("^/advisor/students/(\\d+)/transcript$");

    private final ObjectProvider<PlanOwner.Finder> owners;

    // Makes its own UploadLimit instead of injecting the bean. Every @WebMvcTest loads this class but not
    // our @Components, so injecting it would break every controller's @WebMvcTest.
    // PlanOwner.Finder is an ObjectProvider for the same reason: it's simply missing in those tests
    public UploadErrorHandler(@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize,
            ObjectProvider<PlanOwner.Finder> owners) {
        this.uploadLimit = new UploadLimit(maxFileSize);
        this.owners = owners;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String fileTooLarge(Model model, HttpServletRequest request) {
        model.addAttribute("error", uploadLimit.getMessage());
        model.addAttribute("uploadLimit", uploadLimit);
        advisorsStudent(request).ifPresent(owner -> model.addAttribute("owner", owner));
        return "student/transcript-upload";
    }

    // The file is turned down before any controller runs, so the advisor's version of the page needs its
    // student looked up here. Anyone else (or an advisor who isn't assigned) gets the student's version,
    // which has nothing about the student in it
    private Optional<PlanOwner> advisorsStudent(HttpServletRequest request) {
        Matcher matcher = ADVISOR_UPLOAD.matcher(request.getRequestURI().substring(request.getContextPath().length()));
        PlanOwner.Finder finder = owners.getIfAvailable();
        if (!matcher.matches() || finder == null) {
            return Optional.empty();
        }
        try {
            return finder.find(request.getSession(), Long.valueOf(matcher.group(1)));
        } catch (ResponseStatusException e) {
            return Optional.empty();
        }
    }
}
