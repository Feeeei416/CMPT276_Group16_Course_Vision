package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest 
class CourseOfferingRepositoryTest {
    
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Course saveCourse(String subject, String number) {
        return courseRepository.saveAndFlush(new Course(subject, number, "Some Course",
                new BigDecimal("3.00"), null, null, null));
    }

    private Term saveTerm(int year, Season season) {
        return termRepository.saveAndFlush(new Term(year, season));
    }

    // A saved offering can be loaded back with its course, term and offered flag
    @Test
    void offeringIsSavedAndFoundByCourseAndTerm() {
        Course course = saveCourse("CMPT", "225");
        Term term = saveTerm(2025, Season.SPRING);
        CourseOffering saved = courseOfferingRepository.saveAndFlush(new CourseOffering(course, term, true));
        entityManager.clear();

        CourseOffering found = courseOfferingRepository
            .findByCourseIdAndTermId(course.getId(), term.getId()).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getCourse().getSubject()).isEqualTo("CMPT");
        assertThat(found.getCourse().getNumber()).isEqualTo("225");
        assertThat(found.getTerm().getTermYear()).isEqualTo(2025);
        assertThat(found.getTerm().getSeason()).isEqualTo(Season.SPRING);
        assertThat(found.isOffered()).isTrue();
        assertThat(found.getFetchedAt()).isNotNull();
    }

    // exists is true only when both the course and the term match a saved offering
    @Test
    void existsOnlyForSavedOffering() {
        Course cmpt225 = saveCourse("CMPT", "225");
        Course cmpt276 = saveCourse("CMPT", "276");
        Term spring = saveTerm(2025, Season.SPRING);
        Term fall = saveTerm(2025, Season.FALL);
        courseOfferingRepository.saveAndFlush(new CourseOffering(cmpt225, spring, true));

        assertThat(courseOfferingRepository.existsByCourseIdAndTermId(cmpt225.getId(), spring.getId())).isTrue();
        assertThat(courseOfferingRepository.existsByCourseIdAndTermId(cmpt225.getId(), fall.getId())).isFalse();
        assertThat(courseOfferingRepository.existsByCourseIdAndTermId(cmpt276.getId(), spring.getId())).isFalse();
    }

    // Looking up an offering that was never saved gives an empty result instead of an error
    @Test
    void findReturnsEmptyForMissingOffering() {
        Course course = saveCourse("CMPT", "225");
        Term term = saveTerm(2025, Season.SPRING);

        assertThat(courseOfferingRepository.findByCourseIdAndTermId(course.getId(), term.getId())).isEmpty();
    }

    // "Not offered" is saved as its own row, so it is different from "not checked yet"
    @Test
    void savesNotOfferedOffering() {
        Course course = saveCourse("CMPT", "225");
        Term term = saveTerm(2025, Season.SUMMER);
        courseOfferingRepository.saveAndFlush(new CourseOffering(course, term, false));
        entityManager.clear();

        CourseOffering found = courseOfferingRepository
            .findByCourseIdAndTermId(course.getId(), term.getId()).orElseThrow();
        assertThat(found.isOffered()).isFalse();
    }

    // The same course can't have two offering rows for the same term
    @Test
    void rejectsDuplicateOffering() {
        Course course = saveCourse("CMPT", "225");
        Term term = saveTerm(2025, Season.SPRING);
        courseOfferingRepository.saveAndFlush(new CourseOffering(course, term, true));

        assertThatThrownBy(() -> courseOfferingRepository.saveAndFlush(new CourseOffering(course, term, false)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // The unique rule is on course and term together, so a course can run in many terms
    // and a term can have many courses
    @Test
    void allowsSameCourseOrSameTermInDifferentOfferings() {
        Course cmpt225 = saveCourse("CMPT", "225");
        Course cmpt276 = saveCourse("CMPT", "276");
        Term spring = saveTerm(2025, Season.SPRING);
        Term fall = saveTerm(2025, Season.FALL);
        courseOfferingRepository.saveAndFlush(new CourseOffering(cmpt225, spring, true));
        courseOfferingRepository.saveAndFlush(new CourseOffering(cmpt225, fall, true));
        courseOfferingRepository.saveAndFlush(new CourseOffering(cmpt276, spring, true));

        assertThat(courseOfferingRepository.count()).isEqualTo(3);
    }

    // An offering must belong to a course
    @Test
    void rejectsMissingCourse() {
        Term term = saveTerm(2025, Season.SPRING);

        assertThatThrownBy(() -> courseOfferingRepository.saveAndFlush(new CourseOffering(null, term, true)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // An offering must belong to a term
    @Test
    void rejectsMissingTerm() {
        Course course = saveCourse("CMPT", "225");

        assertThatThrownBy(() -> courseOfferingRepository.saveAndFlush(new CourseOffering(course, null, true)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // A term's offerings come back sorted by subject then number, and only for that term
    @Test
    void findsOfferingsInOneTermSortedByCourseCode() {
        Course macm101 = saveCourse("MACM", "101");
        Course cmpt276 = saveCourse("CMPT", "276");
        Course cmpt225 = saveCourse("CMPT", "225");
        Course cmpt120 = saveCourse("CMPT", "120");
        Term spring = saveTerm(2025, Season.SPRING);
        Term fall = saveTerm(2025, Season.FALL);
        courseOfferingRepository.saveAndFlush(new CourseOffering(macm101, spring, true));
        courseOfferingRepository.saveAndFlush(new CourseOffering(cmpt276, spring, true));
        courseOfferingRepository.saveAndFlush(new CourseOffering(cmpt225, spring, true));
        courseOfferingRepository.saveAndFlush(new CourseOffering(cmpt120, fall, true));
        entityManager.clear();

        assertThat(courseOfferingRepository.findByTermIdOrderByCourseSubjectAscCourseNumberAsc(spring.getId()))
            .extracting(offering -> offering.getCourse().getSubject() + " " + offering.getCourse().getNumber())
            .containsExactly("CMPT 225", "CMPT 276", "MACM 101");
    }

    // A re-fetch can change whether the course is offered, and the change is saved to the same row
    @Test
    void updatedOfferedFlagIsSaved() {
        Course course = saveCourse("CMPT", "225");
        Term term = saveTerm(2025, Season.SPRING);
        CourseOffering saved = courseOfferingRepository.saveAndFlush(new CourseOffering(course, term, true));
        entityManager.clear();

        CourseOffering offering = courseOfferingRepository.findById(saved.getId()).orElseThrow();
        offering.setOffered(false);
        courseOfferingRepository.saveAndFlush(offering);
        entityManager.clear();

        CourseOffering found = courseOfferingRepository.findById(saved.getId()).orElseThrow();
        assertThat(courseOfferingRepository.count()).isEqualTo(1);
        assertThat(found.isOffered()).isFalse();
    }

    // markFetched moves fetchedAt forward and saves it, even when the offered flag didn't change
    @Test
    void markFetchedUpdatesFetchedAt() throws InterruptedException {
        Course course = saveCourse("CMPT", "225");
        Term term = saveTerm(2025, Season.SPRING);
        CourseOffering saved = courseOfferingRepository.saveAndFlush(new CourseOffering(course, term, true));
        entityManager.clear();
        Instant firstFetchedAt = courseOfferingRepository.findById(saved.getId()).orElseThrow().getFetchedAt();

        // Wait a little so the second time is clearly later than the first
        Thread.sleep(10);

        CourseOffering offering = courseOfferingRepository.findById(saved.getId()).orElseThrow();
        offering.markFetched();
        courseOfferingRepository.saveAndFlush(offering);
        entityManager.clear();

        Instant secondFetchedAt = courseOfferingRepository.findById(saved.getId()).orElseThrow().getFetchedAt();
        assertThat(secondFetchedAt).isAfter(firstFetchedAt);
    }
}
