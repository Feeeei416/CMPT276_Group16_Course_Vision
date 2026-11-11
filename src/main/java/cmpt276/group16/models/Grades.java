package cmpt276.group16.models;

import java.util.List;

// Grades a course in a student's record can have, from the SFU Calendar's
// "Undergraduate Grading Systems and Policies" page
public final class Grades {

    // Our own marker for transfer credit; SFU doesn't have a grade for it
    public static final String TRANSFER = "TR";

    // Standard letter grades from best to worst (also FD and N, which count as an F)
    public static final List<String> LETTER = List.of(
            "A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D", "F", "FD", "N");

    // Pass/withdrawn grades, transcript notations (audit, course challenge, credit without grade, ...)
    // and the temporary deferred grade. None of these have a numerical equivalent
    public static final List<String> OTHER = List.of(
            "P", "W", "AE", "AU", "CC", "CF", "CN", "CR", "FX", "WD", "WE", "DE");

    private Grades() {
    }

    // TR counts as valid here; a missing grade (in progress) is checked by the caller
    public static boolean isValid(String grade) {
        return TRANSFER.equals(grade) || LETTER.contains(grade) || OTHER.contains(grade);
    }
}
