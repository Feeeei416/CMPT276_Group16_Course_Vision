package cmpt276.group16.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import cmpt276.group16.models.Grades;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.transcript.Terms;

// Adds up the units in a student's record.
// Used by My courses (#50), and by the eligibility engine (#38) for prerequisites like "60 units"
public final class UnitTotals {

    // earned: units from completed courses. inProgress: units from courses not finished yet
    public record Totals(BigDecimal earned, BigDecimal inProgress) {
    }

    // Newest attempt last, so max() picks the most recent one
    private static final Comparator<TranscriptCourse> OLDEST_FIRST =
            Comparator.comparing(TranscriptCourse::getTerm, Terms.OLDEST_FIRST);

    private UnitTotals() {
    }

    // A course taken more than once counts once:
    //  - if any attempt earned credit, the most recent of those counts
    //  - otherwise, an attempt still in progress counts towards inProgress
    // A course already passed isn't counted again while it's being retaken, so nothing is counted twice.
    // Units come from the grade, not just the units column: a failed course still stores 3.00 there
    public static Totals of(List<TranscriptCourse> courses) {
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal inProgress = BigDecimal.ZERO;

        for (List<TranscriptCourse> attempts : byCourseCode(courses)) {
            Optional<TranscriptCourse> lastPass = attempts.stream()
                    .filter(c -> Grades.earnsCredit(c.getGrade()))
                    .max(OLDEST_FIRST);
            if (lastPass.isPresent()) {
                earned = earned.add(lastPass.get().getUnits());
                continue;
            }
            Optional<TranscriptCourse> current = attempts.stream()
                    .filter(TranscriptCourse::isInProgress)
                    .max(OLDEST_FIRST);
            if (current.isPresent()) {
                inProgress = inProgress.add(current.get().getUnits());
            }
        }
        return new Totals(twoDecimals(earned), twoDecimals(inProgress));
    }

    // "CMPT 225" -> every attempt at CMPT 225. Uppercase so "cmpt 225" added by hand matches the transcript row.
    // Unassigned transfer credit like "MATH 1XX" is never grouped: two of them are two different courses
    private static List<List<TranscriptCourse>> byCourseCode(List<TranscriptCourse> courses) {
        List<List<TranscriptCourse>> groups = new ArrayList<>();
        Map<String, List<TranscriptCourse>> byCode = new LinkedHashMap<>();
        for (TranscriptCourse course : courses) {
            if (isUnassignedTransfer(course)) {
                groups.add(List.of(course));
            } else {
                byCode.computeIfAbsent(code(course), key -> new ArrayList<>()).add(course);
            }
        }
        groups.addAll(byCode.values());
        return groups;
    }

    private static String code(TranscriptCourse course) {
        return (course.getSubject() + " " + course.getNumber()).toUpperCase(Locale.ROOT);
    }

    // Transfer credit that doesn't match one SFU course gets a number like "1XX" or "2XX"
    private static boolean isUnassignedTransfer(TranscriptCourse course) {
        return course.getNumber().toUpperCase(Locale.ROOT).contains("XX");
    }

    // Always show units like the course table does, for example "45.00"
    private static BigDecimal twoDecimals(BigDecimal units) {
        return units.setScale(2, RoundingMode.HALF_UP);
    }
}