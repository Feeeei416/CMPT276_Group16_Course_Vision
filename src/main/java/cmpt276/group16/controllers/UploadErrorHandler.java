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

	private final DataSize maxFileSize;

	public UploadErrorHandler(@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public String fileTooLarge(Model model) {
		model.addAttribute("error", "Upload a file under " + readable(maxFileSize) + ".");
		return "transcript-upload";
	}

	// "1 MB" or "512 KB" instead of DataSize's "1048576B"
	static String readable(DataSize size) {
		if (size.toMegabytes() > 0) {
			return size.toMegabytes() + " MB";
		}
		return size.toKilobytes() + " KB";
	}
}
