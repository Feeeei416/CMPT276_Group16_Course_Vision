package cmpt276.group16.services;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cmpt276.group16.models.Grades;
import cmpt276.group16.models.TranscriptCourse;

// Plain unit tests: no Spring or database, just the counting rules
class UnitTotalsTest {

    // The student isn't needed to count units, so it's left null
    private static TranscriptCourse course(String term, String subject, String number, String units, String grade) {
        return new TranscriptCourse(null, term, subject, number, "Title", new BigDecimal(units), grade);
    }

    private static TranscriptCourse manual(String term, String subject, String number, String units, String grade) {
        return new TranscriptCourse(null, term, subject, number, "Title", new BigDecimal(units), grade,
                TranscriptCourse.Source.MANUAL);
    }

    @Test
    void emptyRecordHasNoUnits() {
        UnitTotals.Totals totals = UnitTotals.of(List.of());

        assertThat(totals.earned()).isEqualByComparingTo("0");
        assertThat(totals.inProgress()).isEqualByComparingTo("0");
    }

    @Test
    void addsUpEveryCompletedCourse() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2025 Fall", "CMPT", "120", "3.00", "A-"),
                course("2025 Fall", "PHYS", "140", "4.00", "B"),
                course("2026 Spring", "MATH", "151", "3.00", "C-")));

        assertThat(totals.earned()).isEqualByComparingTo("10.00");
        assertThat(totals.inProgress()).isEqualByComparingTo("0");
    }

    // Shown like the course table, for example "10.00", not "10"
    @Test
    void totalsHaveTwoDecimals() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(course("2025 Fall", "CMPT", "120", "3", "A")));

        assertThat(totals.earned().toPlainString()).isEqualTo("3.00");
        assertThat(totals.inProgress().toPlainString()).isEqualTo("0.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D", "P"})
    void passingGradesEarnUnits(String grade) {
        assertThat(UnitTotals.of(List.of(course("2025 Fall", "CMPT", "120", "3.00", grade))).earned())
                .isEqualByComparingTo("3.00");
    }

    // A failed course still stores 3.00 units, so the grade decides, not the units column
    @ParameterizedTest
    @ValueSource(strings = {"F", "FD", "N", "W", "WD", "WE", "AU", "DE"})
    void failsWithdrawalsAndAuditsEarnNothing(String grade) {
        UnitTotals.Totals totals = UnitTotals.of(List.of(course("2025 Fall", "CMPT", "120", "3.00", grade)));

        assertThat(totals.earned()).isEqualByComparingTo("0");
        assertThat(totals.inProgress()).isEqualByComparingTo("0");
    }

    @Test
    void transferCreditEarnsUnits() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                manual("2024 Fall", "CMPT", "1XX", "3.00", Grades.TRANSFER)));

        assertThat(totals.earned()).isEqualByComparingTo("3.00");
    }

    @Test
    void inProgressCoursesAreCountedSeparately() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2025 Fall", "CMPT", "120", "3.00", "A"),
                course("2026 Spring", "CMPT", "225", "3.00", null),
                course("2026 Spring", "MACM", "101", "3.00", null)));

        assertThat(totals.earned()).isEqualByComparingTo("3.00");
        assertThat(totals.inProgress()).isEqualByComparingTo("6.00");
    }

    @Test
    void retakeAfterAFailCountsOnce() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2025 Spring", "MATH", "151", "3.00", "F"),
                course("2025 Fall", "MATH", "151", "3.00", "B")));

        assertThat(totals.earned()).isEqualByComparingTo("3.00");
    }

    // A failed attempt to raise a grade doesn't take away the earlier pass
    @Test
    void failedRetakeAfterAPassKeepsThePass() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2025 Spring", "MATH", "151", "3.00", "C-"),
                course("2025 Fall", "MATH", "151", "3.00", "F")));

        assertThat(totals.earned()).isEqualByComparingTo("3.00");
    }

    // Terms are compared as terms, not by the order the rows were saved in
    @Test
    void mostRecentPassIsChosenByTermNotSavingOrder() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2026 Spring", "STAT", "270", "4.00", "A"),
                course("2025 Fall", "STAT", "270", "3.00", "D")));

        assertThat(totals.earned()).isEqualByComparingTo("4.00");
    }

    @Test
    void retakeInProgressAfterAFailCountsAsInProgress() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2025 Fall", "CMPT", "225", "3.00", "F"),
                course("2026 Spring", "CMPT", "225", "3.00", null)));

        assertThat(totals.earned()).isEqualByComparingTo("0");
        assertThat(totals.inProgress()).isEqualByComparingTo("3.00");
    }

    // Retaking a course already passed doesn't add its units a second time
    @Test
    void retakeInProgressAfterAPassIsNotCountedTwice() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2025 Fall", "CMPT", "225", "3.00", "C-"),
                course("2026 Spring", "CMPT", "225", "3.00", null)));

        assertThat(totals.earned()).isEqualByComparingTo("3.00");
        assertThat(totals.inProgress()).isEqualByComparingTo("0");
    }

    @Test
    void coursesAddedByHandCountTheSame() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2025 Fall", "CMPT", "120", "3.00", "A"),
                manual("2025 Fall", "MATH", "100", "3.00", "B")));

        assertThat(totals.earned()).isEqualByComparingTo("6.00");
    }

    // A course added by hand in lowercase is the same course as the transcript row
    @Test
    void courseCodesMatchIgnoringCase() {
        UnitTotals.Totals totals = UnitTotals.of(List.of(
                course("2025 Spring", "MATH", "151", "3.00", "F"),
                manual("2025 Fall", "math", "151", "3.00", "B")));

        assertThat(totals.earned()).isEqualByComparingTo("3.00");
    }
}