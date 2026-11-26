package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import cmpt276.group16.models.Grades;
import cmpt276.group16.models.TranscriptCourse;

// Plain unit tests of the GPA rules from the SFU Calendar's "Undergraduate Grading Systems and Policies"
class GpaTest {

    // The student isn't needed to work out a GPA, so it's left null
    private static TranscriptCourse course(String term, String subject, String number, String units, String grade) {
        return new TranscriptCourse(null, term, subject, number, "Title", new BigDecimal(units), grade);
    }

    private static String cgpa(TranscriptCourse... courses) {
        return Gpa.of(List.of(courses)).cumulativeText();
    }

    @ParameterizedTest
    @CsvSource({"A+, 4.33", "A, 4.00", "A-, 3.67", "B+, 3.33", "B, 3.00", "B-, 2.67", "C+, 2.33", "C, 2.00",
            "C-, 1.67", "D, 1.00", "F, 0", "FD, 0", "N, 0"})
    void everyLetterGradeHasSfusGradePoints(String grade, String points) {
        assertThat(Gpa.gradePoints(grade)).hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo(points));
    }

    @Test
    void emptyRecordHasNoGpa() {
        Gpa.Result gpa = Gpa.of(List.of());

        assertThat(gpa.hasCumulative()).isFalse();
        assertThat(gpa.hasUpperDivision()).isFalse();
        assertThat(gpa.cumulativeText()).isEmpty();
    }

    // Each course's grade points are weighted by its units: (4 x 3 + 3 x 4) / 7 = 3.43
    @Test
    void gpaIsGradePointsWeightedByUnits() {
        assertThat(cgpa(
                course("2025 Fall", "CMPT", "120", "3.00", "A"),
                course("2025 Fall", "CMPT", "201", "4.00", "B"))).isEqualTo("3.43");
    }

    // The SFU Calendar's own example: 39.00 grade points over 16 units is 2.44
    @Test
    void roundsToTwoDecimalPlacesLikeTheCalendarsExample() {
        assertThat(cgpa(
                course("2025 Fall", "CMPT", "120", "3.00", "A"),     // 12.00
                course("2025 Fall", "MATH", "151", "3.00", "B"),     //  9.00
                course("2025 Fall", "MACM", "101", "3.00", "C"),     //  6.00
                course("2025 Fall", "ENGL", "199", "3.00", "C"),     //  6.00
                course("2025 Fall", "PHYS", "120", "4.00", "C+"))) // 9.32, so 42.32 / 16 = 2.65
                .isEqualTo("2.65");
        assertThat(cgpa(
                course("2025 Fall", "CMPT", "120", "4.00", "A"),     // 16.00
                course("2025 Fall", "MATH", "151", "4.00", "B"),     // 12.00
                course("2025 Fall", "MACM", "101", "4.00", "C"),     //  8.00
                course("2025 Fall", "ENGL", "199", "3.00", "D"),     //  3.00
                course("2025 Fall", "PHYS", "120", "1.00", "F")))  // 0, so 39.00 / 16 = 2.44
                .isEqualTo("2.44");
    }

    // A failed course counts, at 0 points: (4 x 3 + 0 x 3) / 6 = 2.00
    @Test
    void failedCourseCountsAtZero() {
        assertThat(cgpa(
                course("2025 Fall", "CMPT", "120", "3.00", "A"),
                course("2025 Fall", "MATH", "151", "3.00", "F"))).isEqualTo("2.00");
    }

    // P, W, CC, CR, AE, audits and transfer credit have no numerical value, so they don't change the GPA
    @ParameterizedTest
    @ValueSource(strings = {"P", "W", "CC", "CR", "AE", "AU", Grades.TRANSFER})
    void gradesWithoutAValueAreLeftOut(String grade) {
        assertThat(cgpa(
                course("2025 Fall", "CMPT", "120", "3.00", "B"),
                course("2025 Fall", "ENSC", "195", "3.00", grade))).isEqualTo("3.00");
    }

    @Test
    void courseInProgressIsLeftOut() {
        assertThat(cgpa(
                course("2025 Fall", "CMPT", "120", "3.00", "B"),
                course("2026 Spring", "CMPT", "125", "3.00", null))).isEqualTo("3.00");
    }

    @Test
    void onlyPassFailGradesMeanNoGpaYet() {
        Gpa.Result gpa = Gpa.of(List.of(course("2025 Fall", "ENSC", "195", "3.00", "P")));

        assertThat(gpa.hasCumulative()).isFalse();
    }

    // A repeated course counts once, with the higher grade, even when the higher one came first
    @Test
    void repeatedCourseCountsTheHigherGrade() {
        assertThat(cgpa(
                course("2025 Spring", "MATH", "151", "3.00", "F"),
                course("2025 Fall", "MATH", "151", "3.00", "B"))).isEqualTo("3.00");
        assertThat(cgpa(
                course("2025 Spring", "MATH", "151", "3.00", "B+"),
                course("2025 Fall", "MATH", "151", "3.00", "C"))).isEqualTo("3.33");
    }

    // Same grade twice: the most recent attempt counts. With different units, that decides the weight
    @Test
    void sameGradeTwiceCountsTheMostRecentAttempt() {
        Gpa.Result gpa = Gpa.of(List.of(
                course("2025 Fall", "CMPT", "120", "3.00", "A"),
                course("2025 Spring", "CMPT", "225", "3.00", "C"),
                course("2026 Spring", "CMPT", "225", "4.00", "C")));

        // (4 x 3 + 2 x 4) / 7 = 2.86, not (12 + 6) / 6 = 3.00
        assertThat(gpa.cumulativeText()).isEqualTo("2.86");
    }

    // A W on the retake doesn't replace the letter grade from the first attempt
    @Test
    void withdrawnRetakeKeepsTheFirstGrade() {
        assertThat(cgpa(
                course("2025 Spring", "MATH", "151", "3.00", "C-"),
                course("2025 Fall", "MATH", "151", "3.00", "W"))).isEqualTo("1.67");
    }

    @Test
    void repeatsMatchIgnoringCase() {
        assertThat(cgpa(
                course("2025 Spring", "MATH", "151", "3.00", "F"),
                course("2025 Fall", "math", "151", "3.00", "A"))).isEqualTo("4.00");
    }

    // Upper division is 300- and 400-level courses, including ones like 405W
    @Test
    void upperDivisionGpaOnlyCounts300And400LevelCourses() {
        Gpa.Result gpa = Gpa.of(List.of(
                course("2025 Fall", "CMPT", "120", "3.00", "C"),
                course("2026 Fall", "CMPT", "300", "3.00", "A"),
                course("2027 Spring", "CMPT", "405W", "3.00", "B")));

        assertThat(gpa.cumulativeText()).isEqualTo("3.00");
        assertThat(gpa.upperDivisionText()).isEqualTo("3.50");
    }

    // Graduate courses (500 and up) aren't upper division, but still count in the CGPA
    @Test
    void graduateCoursesArentUpperDivision() {
        Gpa.Result gpa = Gpa.of(List.of(
                course("2026 Fall", "CMPT", "300", "3.00", "A"),
                course("2027 Spring", "CMPT", "726", "3.00", "C")));

        assertThat(gpa.cumulativeText()).isEqualTo("3.00");
        assertThat(gpa.upperDivisionText()).isEqualTo("4.00");
    }

    @Test
    void noUpperDivisionCoursesMeansNoUpperDivisionGpa() {
        Gpa.Result gpa = Gpa.of(List.of(course("2025 Fall", "CMPT", "120", "3.00", "A")));

        assertThat(gpa.hasCumulative()).isTrue();
        assertThat(gpa.hasUpperDivision()).isFalse();
    }

    // Unassigned transfer credit isn't grouped, and TR has no value anyway
    @Test
    void transferCreditLeavesTheGpaAlone() {
        assertThat(cgpa(
                course("2024 Fall", "MATH", "1XX", "3.00", Grades.TRANSFER),
                course("2025 Fall", "CMPT", "120", "3.00", "B+"))).isEqualTo("3.33");
    }
}
