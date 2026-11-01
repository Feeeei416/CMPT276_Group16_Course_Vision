package cmpt276.group16.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

// The largest upload allowed, read from spring.servlet.multipart.max-file-size so it is only set in one place.
// The upload page also gets it, to check the size in the browser before sending
@Component
public class UploadLimit {

    private final DataSize maxFileSize;

    public UploadLimit(@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public long getBytes() {
        return maxFileSize.toBytes();
    }

    public String getMessage() {
        return "Upload a file under " + readable(maxFileSize) + ".";
    }

    // "1 MB" or "512 KB" instead of DataSize's "1048576B"
    static String readable(DataSize size) {
        if (size.toMegabytes() > 0) {
            return size.toMegabytes() + " MB";
        }
        return size.toKilobytes() + " KB";
    }
}
