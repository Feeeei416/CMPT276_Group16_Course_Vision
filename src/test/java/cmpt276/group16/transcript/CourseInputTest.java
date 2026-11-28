package cmpt276.group16.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.User;

// Plain unit tests: no Spring or database, just the Add/Edit course form checks
class CourseInputTest {

    private static Map<String, String> validForm() {
        Map<String, String> form = new HashMap<>();
        form.put("year", "2025");
        form.put("season", "Fall");
        form.put("subject", "CMPT");
        form.put("number", "120");
        form.put("title", "Intro.Cmpt.Sci/Programming I");
        form.put("units", "3.00");
        form.put("grade", "A-");
        return form;
    }

    private static Map<String, String> validFormWith(String field, String value) {
        Map<String, String> form = validForm();
        form.put(field, value);
        return form;
    }

    @Test
    void validFormHasNoErrors() {
        assertThat(CourseInput.validate(validForm())).isEmpty();
    }

    @Test
    void emptyFormReportsEveryRequiredField() {
        assertThat(CourseInput.validate(Map.of())).containsExactly(
                "Year is required.",
                "Choose a term.",
                "Subject is required.",
                "Course number is required.",
                "Title is required.",
                "Units are required.");
    }

    @Test
    void readsTheFormIntoACourse() {
        CourseInput course = CourseInput.from(validForm());

        assertThat(course.term()).isEqualTo("2025 Fall");
        assertThat(course.subject()).isEqualTo("CMPT");
        assertThat(course.number()).isEqualTo("120");
        assertThat(course.title()).isEqualTo("Intro.Cmpt.Sci/Programming I");
        assertThat(course.units()).isEqualTo(new BigDecimal("3.00"));
        assertThat(course.grade()).isEqualTo("A-");
    }

    // Students will type "cmpt" and " 3 ", which should save the same as the transcript would
    @Test
    void tidiesWhatTheStudentTyped() {
        Map<String, String> form = validForm();
        form.put("subject", " cmpt ");
        form.put("number", "105w");
        form.put("title", "  Social Issues  ");
        form.put("units", " 3 ");
        form.put("grade", "b+");

        assertThat(CourseInput.validate(form)).isEmpty();
        CourseInput course = CourseInput.from(form);
        assertThat(course.subject()).isEqualTo("CMPT");
        assertThat(course.number()).isEqualTo("105W");
        assertThat(course.title()).isEqualTo("Social Issues");
        assertThat(course.units()).isEqualTo(new BigDecimal("3.00"));
        assertThat(course.grade()).isEqualTo("B+");
    }

    @Test
    void emptyGradeMeansInProgress() {
        Map<String, String> form = validFormWith("grade", "");

        assertThat(CourseInput.validate(form)).isEmpty();
        assertThat(CourseInput.from(form).grade()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TR", "A+", "C-", "F", "P", "W", "CR"})
    void acceptsSfuGradesAndTransferCredit(String grade) {
        assertThat(CourseInput.validate(validFormWith("grade", grade))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"E", "A++", "D-", "IP", "pass"})
    void rejectsGradesSfuDoesntUse(String grade) {
        assertThat(CourseInput.validate(validFormWith("grade", grade))).containsExactly("Choose a grade from the list.");
    }

    // Acceptance criterion: units that aren't a number show an error.
    // #76: SFU courses are worth whole units, so 3.01 and 3.5 are turned down too
    @ParameterizedTest
    @ValueSource(strings = {"three", "3,00", "-1", "10", "15", "3.005", "3.01", "3.5", "0.5"})
    void rejectsUnitsThatArentAWholeNumber(String units) {
        assertThat(CourseInput.validate(validFormWith("units", units)))
                .containsExactly("Units must be a whole number from 0 to 9, such as 3.");
    }

    // "3.00" is how transcripts print units, so it still counts as 3.
    // 9 is ENSC 499, the largest CMPT or ENSC course; 6 is CMPT 498, the CMPT honours project
    @ParameterizedTest
    @ValueSource(strings = {"3", "3.00", "4.0", "6", "9", "9.00"})
    void acceptsWholeNumberUnits(String units) {
        assertThat(CourseInput.validate(validFormWith("units", units))).isEmpty();
    }

    // #76 Edit: the course's units now are always allowed, since uploads aren't capped
    @ParameterizedTest
    @ValueSource(strings = {"15", "15.00"})
    void keepingUnitsOverTheCapIsAllowed(String units) {
        Map<String, String> form = validFormWith("units", units);
        BigDecimal kept = new BigDecimal("15.00");

        assertThat(CourseInput.validate(form, kept)).isEmpty();
        assertThat(CourseInput.from(form, kept).units()).isEqualTo(new BigDecimal("15.00"));
    }

    @Test
    void keepingAPartUnitIsAllowed() {
        Map<String, String> form = validFormWith("units", "4.5");

        assertThat(CourseInput.validate(form, new BigDecimal("4.50"))).isEmpty();
        assertThat(CourseInput.from(form, new BigDecimal("4.50")).units()).isEqualTo(new BigDecimal("4.50"));
    }

    @Test
    void changingToOtherUnitsStillHasTheUsualCheck() {
        assertThat(CourseInput.validate(validFormWith("units", "12"), new BigDecimal("15.00")))
                .containsExactly("Units must be a whole number from 0 to 9, such as 3.");
        assertThat(CourseInput.validate(validFormWith("units", "3.5"), new BigDecimal("4.50")))
                .containsExactly("Units must be a whole number from 0 to 9, such as 3.");
    }

    // Co-op terms are worth 0 units
    @Test
    void acceptsZeroUnits() {
        assertThat(CourseInput.validate(validFormWith("units", "0"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1964", "3000", "twenty", "2025.5"})
    void rejectsYearsOutsideTheRange(String year) {
        assertThat(CourseInput.validate(validFormWith("year", year)))
                .containsExactly("Year must be between 1965 and " + CourseInput.maxYear() + ".");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "fall", "Winter"})
    void seasonMustBeFromTheList(String season) {
        assertThat(CourseInput.validate(validFormWith("season", season))).containsExactly("Choose a term.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"C", "CMPTS", "CMP1", "C MPT"})
    void subjectMustBe2To4Letters(String subject) {
        assertThat(CourseInput.validate(validFormWith("subject", subject)))
                .containsExactly("Subject must be 2 to 4 letters, such as CMPT.");
    }

    // 1XX is how transfer credit that doesn't match one SFU course is numbered
    @ParameterizedTest
    @ValueSource(strings = {"225", "105W", "1XX", "2XXW"})
    void acceptsCourseNumbersLikeSfus(String number) {
        assertThat(CourseInput.validate(validFormWith("number", number))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"22", "2250", "X25", "225WW"})
    void rejectsOtherCourseNumbers(String number) {
        assertThat(CourseInput.validate(validFormWith("number", number)))
                .containsExactly("Course number must look like 225, 105W or 1XX.");
    }

    @Test
    void titleHasALengthLimit() {
        assertThat(CourseInput.validate(validFormWith("title", "x".repeat(256))))
                .containsExactly("Title must be at most 255 characters.");
    }

    // Edit starts with the course's current values, and they have to pass the same checks
    @Test
    void formForACourseRoundTrips() {
        User user = new User("priya@example.com", "hash", "Priya", Role.STUDENT);
        StudentProfile student = new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024);
        TranscriptCourse course = new TranscriptCourse(student, "2026 Spring", "CMPT", "125",
                "Intro.Cmpt.Sci/Programming II", new BigDecimal("3.00"), null);

        Map<String, String> form = CourseInput.formFor(course);

        // "3", not "3.00", so it fits the form's whole-number units box
        assertThat(form).containsEntry("year", "2026").containsEntry("season", "Spring").containsEntry("grade", "")
                .containsEntry("units", "3");
        assertThat(CourseInput.validate(form)).isEmpty();
        assertThat(CourseInput.from(form)).isEqualTo(new CourseInput("2026 Spring", "CMPT", "125",
                "Intro.Cmpt.Sci/Programming II", new BigDecimal("3.00"), null));
    }
}
