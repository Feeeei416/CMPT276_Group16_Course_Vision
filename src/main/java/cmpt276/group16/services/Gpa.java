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

import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.transcript.Terms;

// A student's cumulative GPA (CGPA) and upper-division GPA (UDGPA), worked out from their record the way the
// SFU Calendar describes ("Undergraduate Grading Systems and Policies"):
//  - each letter grade has a grade point value, from A+ 4.33 down to F, FD and N at 0
//  - GPA = total grade points / total units of letter-graded courses, to two decimal places.
//    A failed course counts, at 0 points
//  - P, W, CC, CR, AE, audits, transfer credit and courses in progress have no numerical value, so they're left out
//  - a repeated course counts once: only the higher grade, or the most recent attempt when the grades are the same
//  - the UDGPA is the same over 300- and 400-level courses only
// It's an estimate from what's in the record, so My courses says so: SFU's own number is on the official transcript
public final class Gpa {

    // From the SFU Calendar's grading scale
    static final Map<String, BigDecimal> GRADE_POINTS = Map.ofEntries(
            Map.entry("A+", new BigDecimal("4.33")),
            Map.entry("A", new BigDecimal("4.00")),
            Map.entry("A-", new BigDecimal("3.67")),
            Map.entry("B+", new BigDecimal("3.33")),
            Map.entry("B", new BigDecimal("3.00")),
            Map.entry("B-", new BigDecimal("2.67")),
            Map.entry("C+", new BigDecimal("2.33")),
            Map.entry("C", new BigDecimal("2.00")),
            Map.entry("C-", new BigDecimal("1.67")),
            Map.entry("D", new BigDecimal("1.00")),
            Map.entry("F", BigDecimal.ZERO),
            // FD (failed for academic dishonesty) and N (did not complete) count as an F
            Map.entry("FD", BigDecimal.ZERO),
            Map.entry("N", BigDecimal.ZERO));

    // cumulative and upperDivision are empty until there's a letter grade to count
    public record Result(Optional<BigDecimal> cumulative, Optional<BigDecimal> upperDivision) {

        // For example "3.42", or "" when there's no GPA yet
        public String cumulativeText() {
            return cumulative.map(BigDecimal::toPlainString).orElse("");
        }

        public String upperDivisionText() {
            return upperDivision.map(BigDecimal::toPlainString).orElse("");
        }

        public boolean hasCumulative() {
            return cumulative.isPresent();
        }

        public boolean hasUpperDivision() {
            return upperDivision.isPresent();
        }
    }

    private static final Comparator<TranscriptCourse> OLDEST_FIRST =
            Comparator.comparing(TranscriptCourse::getTerm, Terms.OLDEST_FIRST);

    private Gpa() {
    }

    public static Result of(List<TranscriptCourse> courses) {
        List<TranscriptCourse> counted = new ArrayList<>();
        for (List<TranscriptCourse> attempts : byCourseCode(courses)) {
            countedAttempt(attempts).ifPresent(counted::add);
        }
        return new Result(average(counted),
                average(counted.stream().filter(Gpa::isUpperDivision).toList()));
    }

    // The grade point value of a letter grade; empty for anything that doesn't count (P, W, TR, in progress...)
    static Optional<BigDecimal> gradePoints(String grade) {
        return grade == null ? Optional.empty() : Optional.ofNullable(GRADE_POINTS.get(grade));
    }

    // Of all the attempts at one course, the one that counts: the highest grade, and the most recent of
    // attempts with the same grade. Empty when none has a letter grade
    private static Optional<TranscriptCourse> countedAttempt(List<TranscriptCourse> attempts) {
        Comparator<TranscriptCourse> byPoints = Comparator.comparing(c -> gradePoints(c.getGrade()).orElseThrow());
        return attempts.stream()
                .filter(c -> gradePoints(c.getGrade()).isPresent())
                .max(byPoints.thenComparing(OLDEST_FIRST));
    }

    // Courses worth 0 units add nothing to either side of the division, so they're left out
    private static Optional<BigDecimal> average(List<TranscriptCourse> courses) {
        BigDecimal points = BigDecimal.ZERO;
        BigDecimal units = BigDecimal.ZERO;
        for (TranscriptCourse course : courses) {
            if (course.getUnits() == null || course.getUnits().signum() <= 0) {
                continue;
            }
            points = points.add(gradePoints(course.getGrade()).orElseThrow().multiply(course.getUnits()));
            units = units.add(course.getUnits());
        }
        if (units.signum() == 0) {
            return Optional.empty();
        }
        return Optional.of(points.divide(units, 2, RoundingMode.HALF_UP));
    }

    // 300- and 400-level courses, for example CMPT 300 or CMPT 405W
    static boolean isUpperDivision(TranscriptCourse course) {
        String number = course.getNumber();
        // Graduate courses (500 and up, like CMPT 726) aren't upper division
        return !number.isEmpty() && (number.charAt(0) == '3' || number.charAt(0) == '4');
    }

    // The same grouping as UnitTotals: "CMPT 225" in any case is one course, and unassigned transfer credit
    // like "MATH 1XX" is never grouped, since two of them are two different courses
    private static List<List<TranscriptCourse>> byCourseCode(List<TranscriptCourse> courses) {
        List<List<TranscriptCourse>> groups = new ArrayList<>();
        Map<String, List<TranscriptCourse>> byCode = new LinkedHashMap<>();
        for (TranscriptCourse course : courses) {
            if (course.getNumber().toUpperCase(Locale.ROOT).contains("XX")) {
                groups.add(List.of(course));
            } else {
                String code = (course.getSubject() + " " + course.getNumber()).toUpperCase(Locale.ROOT);
                byCode.computeIfAbsent(code, key -> new ArrayList<>()).add(course);
            }
        }
        groups.addAll(byCode.values());
        return groups;
    }
}
