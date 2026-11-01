package cmpt276.group16.controllers;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// Files over spring.servlet.multipart.max-file-size are rejected before they reach TranscriptController,
// so the error is caught here instead
@ControllerAdvice
public class UploadErrorHandler {

    private final UploadLimit uploadLimit;

    public UploadErrorHandler(UploadLimit uploadLimit) {
        this.uploadLimit = uploadLimit;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String fileTooLarge(Model model) {
        model.addAttribute("error", uploadLimit.getMessage());
        model.addAttribute("uploadLimit", uploadLimit);
        return "student/transcript-upload";
    }
}
