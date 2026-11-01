package cmpt276.group16.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

// Files over spring.servlet.multipart.max-file-size are rejected before they reach TranscriptController,
// so the error is caught here instead
@ControllerAdvice
public class UploadErrorHandler {

    private final UploadLimit uploadLimit;

    // Makes its own UploadLimit instead of injecting the bean. Every @WebMvcTest loads this class but not
    // our @Components, so injecting it would break every controller's @WebMvcTest
    public UploadErrorHandler(@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.uploadLimit = new UploadLimit(maxFileSize);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String fileTooLarge(Model model) {
        model.addAttribute("error", uploadLimit.getMessage());
        model.addAttribute("uploadLimit", uploadLimit);
        return "student/transcript-upload";
    }
}
