package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import cmpt276.group16.models.Season;
import cmpt276.group16.services.PlanRules.CourseCode;

// Plain unit tests: no Spring or database, just the rules
class PlanRulesTest {

    // A day in Fall 2026, so next semester is Spring 2027
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @ParameterizedTest
    @CsvSource({
        "CMPT 225,     CMPT, 225",
        "cmpt225,      CMPT, 225",
        "CMPT  276,    CMPT, 276",
        "'  macm 201 ', MACM, 201",
        "CMPT 105w,    CMPT, 105W",
        "ENSC 100W,    ENSC, 100W"
    })
    void readsCourseCodes(String text, String subject, String number) {
        assertThat(PlanRules.parseCourseCode(text)).contains(new CourseCode(subject, number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CMPT", "225", "CMPT 22", "CMPT 2255", "CMPTX 225", "C 225", "CMPT-225", "CMPT 225 276"})
    void rejectsTextThatIsntACourseCode(String text) {
        assertThat(PlanRules.parseCourseCode(text)).isEmpty();
    }

    // Next semester changes on the first day of January, May and September
    @ParameterizedTest
    @CsvSource({
        "2026-11-09, 2027 Spring",
        "2026-12-31, 2027 Spring",
        "2027-01-01, 2027 Summer",
        "2027-04-30, 2027 Summer",
        "2027-05-01, 2027 Fall",
        "2027-09-01, 2028 Spring"
    })
    void findsNextSemester(LocalDate today, String nextSemester) {
        assertThat(PlanRules.nextSemester(today)).isEqualTo(nextSemester);
    }

    // The first term after next semester, the last term allowed, and one in between
    @ParameterizedTest
    @CsvSource({"2027, SUMMER", "2027, FALL", "2030, SPRING", "2033, FALL"})
    void acceptsTermsAfterNextSemester(int year, Season season) {
        assertThat(PlanRules.problemsWithTerm(year, season, TODAY)).isEmpty();
    }

    // Next semester is planned on its own schedule page (#40), and earlier terms are already on the transcript
    @ParameterizedTest
    @CsvSource({"2027, SPRING", "2026, FALL", "2020, FALL"})
    void rejectsNextSemesterAndEarlier(int year, Season season) {
        assertThat(PlanRules.problemsWithTerm(year, season, TODAY))
            .containsExactly("Choose a term after next semester (2027 Spring).");
    }

    @Test
    void rejectsTermsTooFarAhead() {
        assertThat(PlanRules.problemsWithTerm(2034, Season.SPRING, TODAY))
            .containsExactly("Choose a term no later than 2033 Fall.");
    }

    // The form sends FALL, but any case is read the same way
    @ParameterizedTest
    @ValueSource(strings = {"FALL", "Fall", " fall "})
    void readsSeasonsInAnyCase(String text) {
        assertThat(PlanRules.parseSeason(text)).contains(Season.FALL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Winter", "Autumn", ""})
    void rejectsUnknownSeasons(String text) {
        assertThat(PlanRules.parseSeason(text)).isEmpty();
    }

    @Test
    void offersYearsFromTheFirstPlannableTermToTheLast() {
        assertThat(PlanRules.plannableYears(TODAY))
            .containsExactly(2027, 2028, 2029, 2030, 2031, 2032, 2033);
    }

    // In Fall 2027, next semester is Spring 2028, so 2027 has nothing left to plan
    @Test
    void skipsAYearWithNoPlannableTermsLeft() {
        assertThat(PlanRules.plannableYears(LocalDate.of(2027, 10, 1))).startsWith(2028);
    }
}
