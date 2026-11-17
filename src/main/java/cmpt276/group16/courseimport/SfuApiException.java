package cmpt276.group16.courseimport;

// Thrown when the SFU Course Outlines API can't be used right now: it can't be reached, times out,
// or answers with an error other than 404. The import stops and keeps the existing course data
public class SfuApiException extends RuntimeException{
    
    public SfuApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
