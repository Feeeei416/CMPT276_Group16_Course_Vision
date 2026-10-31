package cmpt276.group16.transcript;

import java.io.Serializable;
import java.math.BigDecimal;

// One course read from a transcript before the student confirms it
// Serializable so it can wait in the session (sessions are saved in the database)
public record ParsedCourse(
        String term,
        String subject,
        String number,
        String title,
        BigDecimal units,
        String grade) implements Serializable {

    // Keeps saved sessions readable
    private static final long serialVersionUID = 1L;

    // Courses that are still in progress will have no grade yet
    public boolean isInProgress() {
        return grade == null;
    }
}
