package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.CourseOfferingRepository;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.YearAndSeason;

// Uses the test database (H2) for termsOffered, since it reads saved offerings
@DataJpaTest 
class PastOfferingsTest {
    // In 2026 Fall, so the recent terms are 2024 Fall to 2026 Summer
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Autowired
    private CourseOfferingRepository offeringRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    private PastOfferings pastOfferings;

    @BeforeEach 
    void setUp() {
        pastOfferings = new PastOfferings(offeringRepository);
    }

    private Course saveCourse(String number) {
        return courseRepository.save(new Course("CMPT", number, "Test course", new BigDecimal("3"), null, null, null));
    }

    // Saves the term the first time it's needed, so two courses can share it
    private Term term(int year, Season season) {
        return termRepository.findByTermYearAndSeason(year, season)
                .orElseGet(() -> termRepository.save(new Term(year, season)));
    }

    private void saveOffering(Course course, int year, Season season, boolean offered) {
        offeringRepository.save(new CourseOffering(course, term(year, season), offered));
    }

    // -- recentTerms --

    // The 6 terms before this one, going back across years
    @Test
    void recentTermsAreTheSixBeforeThisOne() {
        assertThat(PastOfferings.recentTerms(TODAY)).containsExactly(
                new YearAndSeason(2024, Season.FALL), new YearAndSeason(2025, Season.SPRING), 
                new YearAndSeason(2025, Season.SUMMER), new YearAndSeason(2025, Season.FALL), 
                new YearAndSeason(2026, Season.SPRING), new YearAndSeason(2026, Season.SUMMER));
    }

    // On the first day of a term, that term is this term, so it isn't in the list yet
    @Test
    void recentTermsOnTheFirstDayOfATerm() {
        LocalDate date = LocalDate.of(2027, 1, 1);
        assertThat(PastOfferings.recentTerms(date)).containsExactly(
                new YearAndSeason(2025, Season.SPRING), new YearAndSeason(2025, Season.SUMMER), 
                new YearAndSeason(2025, Season.FALL), new YearAndSeason(2026, Season.SPRING), 
                new YearAndSeason(2026, Season.SUMMER), new YearAndSeason(2026, Season.FALL));

    }

    // -- termsOffered --

    // Only terms the course ran in that are among the recent terms count
    @Test
    void onlyRecentTermsItRanInCount() {
        Course course = saveCourse("225");
        saveOffering(course, 2023, Season.FALL, true);
        saveOffering(course, 2025, Season.FALL, true);
        saveOffering(course, 2026, Season.SPRING, false);
        saveOffering(course, 2026, Season.FALL, true);
        saveOffering(course, 2027, Season.SPRING, true);
       
        assertThat(pastOfferings.termsOffered(course.getId(), TODAY)).containsExactly(
                new YearAndSeason(2025,  Season.FALL));
    }

    // The result goes oldest first, whatever order the offerings were saved in
    @Test
    void termsComeOldestFirst() {
        Course course = saveCourse("225");
        saveOffering(course, 2026, Season.SUMMER, true);
        saveOffering(course, 2025, Season.SPRING, true);
        saveOffering(course, 2025, Season.FALL, true);

        assertThat(pastOfferings.termsOffered(course.getId(), TODAY)).containsExactly(
                new YearAndSeason(2025, Season.SPRING),
                new YearAndSeason(2025, Season.FALL),
                new YearAndSeason(2026, Season.SUMMER));
    }

    // Another course's offerings don't count
    @Test
    void onlyThisCoursesOfferingsCount() {
        Course course = saveCourse("225");
        Course other = saveCourse("276");
        saveOffering(course, 2025, Season.FALL, true);
        saveOffering(other, 2026, Season.SPRING, true);

        assertThat(pastOfferings.termsOffered(course.getId(), TODAY)).containsExactly(
                new YearAndSeason(2025,  Season.FALL));
    }

    @Test
    void courseWithNoOfferingsWasOfferedInNone() {
        Course course = saveCourse("225");

        assertThat(pastOfferings.termsOffered(course.getId(), TODAY)).isEmpty();
    }
}
