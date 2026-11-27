package cmpt276.group16.transcript;

import java.io.Serializable;
import java.math.BigDecimal;

import cmpt276.group16.models.Grades;

// One course read from a transcript before the student confirms it
// Serializable so it can wait in the session (sessions are saved in the database)
public record ParsedCourse(
        String term,
        String subject,
        String number,
        String title,
        BigDecimal units,
        String grade,
        String topic ) implements Serializable {

    // Keeps saved sessions readable
    private static final long serialVersionUID = 1L;

    //Normal courses(not special courses) have no "Course Topic" so their topic is null
    public ParsedCourse(String term, String subject, String number, String title, BigDecimal units, String grade){
        this(term, subject, number, title, units, grade, null);
    }

    // The same course with its topic filled in. Records can't be changed, so this makes a copy
    public ParsedCourse withTopic(String topic) {
        return new ParsedCourse(term, subject, number, title, units, grade, topic);
    }

    // Courses that are still in progress will have no grade yet
    public boolean isInProgress() {
        return grade == null;
    }

    // The course table also shows saved courses, which can be transfer credit, which won't be added by a transcript
    public boolean isTransferCredit() {
        return Grades.TRANSFER.equals(grade);
    }
}
