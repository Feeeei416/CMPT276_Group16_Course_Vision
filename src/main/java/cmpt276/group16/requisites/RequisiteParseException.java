package cmpt276.group16.requisites;

// Thrown when prerequisite text can't be turned into a Requirement.
// The course is then flagged and the student is shown the original text; the message is for us, not the student
public class RequisiteParseException extends RuntimeException {

    public RequisiteParseException(String message) {
        super(message);
    }
}
