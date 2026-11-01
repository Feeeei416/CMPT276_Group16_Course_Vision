package cmpt276.group16.transcript;

// Thrown when an upload can't be turned into courses, the message is shown to the uploader
public class TranscriptParseException extends RuntimeException {

    public TranscriptParseException(String message) {
        super(message);
    }
}
