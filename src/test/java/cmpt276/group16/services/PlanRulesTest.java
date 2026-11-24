package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import cmpt276.group16.models.Season;
import cmpt276.group16.models.TermKind;
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

    // On a computer set to Turkish, a plain toUpperCase() turns "i" into "İ", so BISC would never match a course
    @Test
    void capitalsDontDependOnTheComputersLanguage() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(PlanRules.parseCourseCode("bisc 101")).contains(new CourseCode("BISC", "101"));
        } finally {
            Locale.setDefault(before);
        }
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

    // This term changes on the same days, and is always the term before next semester
    @ParameterizedTest
    @CsvSource({
        "2026-11-09, 2026, FALL",
        "2026-12-31, 2026, FALL",
        "2027-01-01, 2027, SPRING",
        "2027-04-30, 2027, SPRING",
        "2027-05-01, 2027, SUMMER",
        "2027-09-01, 2027, FALL"
    })
    void findsThisTerm(LocalDate today, int year, Season season) {
        assertThat(PlanRules.currentTermYearAndSeason(today)).isEqualTo(new PlanRules.YearAndSeason(year, season));
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

    @Test
    void acceptsAPlanNameUpToTheLimit() {
        assertThat(PlanRules.problemsWithPlanName("Co-op plan")).isEmpty();
        assertThat(PlanRules.problemsWithPlanName("a".repeat(40))).isEmpty();
    }

    @Test
    void rejectsABlankPlanName() {
        assertThat(PlanRules.problemsWithPlanName("")).containsExactly("Give the plan a name.");
    }

    @Test
    void rejectsAPlanNameOverTheLimit() {
        assertThat(PlanRules.problemsWithPlanName("a".repeat(41)))
            .containsExactly("Plan names can be at most 40 characters.");
    }

    @Test
    void scheduleNamesFollowTheSameRules() {
        assertThat(PlanRules.problemsWithScheduleName("Morning labs")).isEmpty();
        assertThat(PlanRules.problemsWithScheduleName("a".repeat(40))).isEmpty();
        assertThat(PlanRules.problemsWithScheduleName("")).containsExactly("Give the schedule a name.");
        assertThat(PlanRules.problemsWithScheduleName("a".repeat(41)))
            .containsExactly("Schedule names can be at most 40 characters.");
    }

    // In Fall 2027, next semester is Spring 2028, so 2027 has nothing left to plan
    @Test
    void skipsAYearWithNoPlannableTermsLeft() {
        assertThat(PlanRules.plannableYears(LocalDate.of(2027, 10, 1))).startsWith(2028);
    }

    // The form sends COOP, but any case is read the same way, and blank means a study term
    @ParameterizedTest
    @CsvSource({"COOP, COOP", "coop, COOP", " Gap , GAP", "STUDY, STUDY", "'', STUDY"})
    void readsTermKinds(String text, TermKind kind) {
        assertThat(PlanRules.parseTermKind(text)).contains(kind);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Co-op", "Internship", "Exchange"})
    void rejectsUnknownTermKinds(String text) {
        assertThat(PlanRules.parseTermKind(text)).isEmpty();
    }

    // CMPT Practicum I to V and the ENSC internships, from the SFU calendar
    @ParameterizedTest
    @CsvSource({"CMPT, 426", "CMPT, 430", "ENSC, 195", "ENSC, 395"})
    void knowsCoopWorkTermCourses(String subject, String number) {
        assertThat(PlanRules.isCoopCourse(subject, number)).isTrue();
    }

    // Normal courses, and CMPT 493 Digital Media Practicum, which is a taught course and not a work term
    @ParameterizedTest
    @CsvSource({"CMPT, 225", "CMPT, 425", "CMPT, 431", "CMPT, 493", "ENSC, 251"})
    void otherCoursesArentCoopCourses(String subject, String number) {
        assertThat(PlanRules.isCoopCourse(subject, number)).isFalse();
    }
}
