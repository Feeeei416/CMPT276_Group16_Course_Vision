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
class CourseRepositoryTest {
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Course newCourse(String subject, String number) {
        return new Course(subject, number, "Data Structures and Programming", new BigDecimal("3.00"),
                "Some description", "CMPT 125 with a C- or better", null);
    }

    private Course saveCourse(String subject, String number) {
        return courseRepository.saveAndFlush(newCourse(subject, number));
    }

    // A saved course can be loaded back with all of its details
    @Test
    void courseIsSavedAndFoundBySubjectAndNumber() {
        Course saved = saveCourse("CMPT", "225");
        entityManager.clear();

        Course found = courseRepository.findBySubjectAndNumber("CMPT", "225").orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getTitle()).isEqualTo("Data Structures and Programming");
        assertThat(found.getUnits()).isEqualByComparingTo("3");
        assertThat(found.getDescription()).isEqualTo("Some description");
        assertThat(found.getPrereqText()).isEqualTo("CMPT 125 with a C- or better");
        assertThat(found.getCoreqText()).isNull();
        assertThat(found.getFetchedAt()).isNotNull();
    }

    // Subject and number are stored uppercase and without extra spaces, whatever the input looks like
    @Test
    void subjectAndNumberAreStoredUppercase() {
        Course saved = saveCourse(" cmpt ", "105w");

        assertThat(saved.getSubject()).isEqualTo("CMPT");
        assertThat(saved.getNumber()).isEqualTo("105W");
    }

    // findByNormalizedSubjectAndNumber finds the course even when the input is lowercase or has spaces
    @Test
    void findByNormalizedSubjectAndNumberIgnoresCase() {
        saveCourse("CMPT", "105W");
        entityManager.clear();

        assertThat(courseRepository.findByNormalizedSubjectAndNumber("cmpt", "105w")).isPresent();
        assertThat(courseRepository.findByNormalizedSubjectAndNumber(" Cmpt ", " 105W ")).isPresent();
    }

    // Looking up a course that was never saved gives an empty result instead of an error
    @Test
    void findReturnsEmptyForMissingCourse() {
        assertThat(courseRepository.findByNormalizedSubjectAndNumber("CMPT", "225")).isEmpty();
    }

    // The same subject and number can't be saved twice
    @Test
    void rejectsDuplicateCourse() {
        saveCourse("CMPT", "225");

        assertThatThrownBy(() -> saveCourse("CMPT", "225"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // "cmpt 225" and "CMPT 225" are the same course, so the second one is also a duplicate
    @Test
    void rejectsDuplicateCourseWithDifferentCase() {
        saveCourse("CMPT", "225");

        assertThatThrownBy(() -> saveCourse("cmpt", "225"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // The unique rule is on subject and number together, so either one can repeat on its own
    @Test
    void allowsSameSubjectOrSameNumberInDifferentCourses() {
        saveCourse("CMPT", "225");
        saveCourse("CMPT", "276");
        saveCourse("MACM", "225");

        assertThat(courseRepository.count()).isEqualTo(3);
    }

    // Many courses have no prerequisites, corequisites or description, so these can be empty
    @Test
    void allowsMissingDescriptionAndRequisites() {
        Course course = new Course("CMPT", "120", "Intro to Computing Science", new BigDecimal("3.00"),
                null, null, null);
        Course saved = courseRepository.saveAndFlush(course);
        entityManager.clear();

        Course found = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getDescription()).isNull();
        assertThat(found.getPrereqText()).isNull();
        assertThat(found.getCoreqText()).isNull();
    }

    // Long text fits, because description and requisite text are TEXT columns, not 255-character ones
    @Test
    void savesTextLongerThan255Characters() {
        String longText = "x".repeat(2000);
        Course course = new Course("CMPT", "225", "Data Structures", new BigDecimal("3.00"),
                longText, longText, longText);
        Course saved = courseRepository.saveAndFlush(course);
        entityManager.clear();

        Course found = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getDescription()).hasSize(2000);
        assertThat(found.getPrereqText()).hasSize(2000);
        assertThat(found.getCoreqText()).hasSize(2000);
    }

    // A course must have a title
    @Test
    void rejectsMissingTitle() {
        Course course = new Course("CMPT", "225", null, new BigDecimal("3.00"), null, null, null);

        assertThatThrownBy(() -> courseRepository.saveAndFlush(course))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // A course must have units
    @Test
    void rejectsMissingUnits() {
        Course course = new Course("CMPT", "225", "Data Structures", null, null, null, null);

        assertThatThrownBy(() -> courseRepository.saveAndFlush(course))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // A re-fetch can change the course details, and the changes are saved to the same row
    @Test
    void updatedDetailsAreSaved() {
        Course saved = saveCourse("CMPT", "225");
        entityManager.clear();

        Course course = courseRepository.findById(saved.getId()).orElseThrow();
        course.setTitle("New Title");
        course.setUnits(new BigDecimal("4.00"));
        course.setDescription("New description");
        course.setPrereqText(null);
        course.setCoreqText("CMPT 295");
        courseRepository.saveAndFlush(course);
        entityManager.clear();

        Course found = courseRepository.findById(saved.getId()).orElseThrow();
        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(found.getTitle()).isEqualTo("New Title");
        assertThat(found.getUnits()).isEqualByComparingTo("4");
        assertThat(found.getDescription()).isEqualTo("New description");
        assertThat(found.getPrereqText()).isNull();
        assertThat(found.getCoreqText()).isEqualTo("CMPT 295");
    }

    // markFetched moves fetchedAt forward and saves it, even when nothing else about the course changed
    @Test
    void markFetchedUpdatesFetchedAt() throws InterruptedException {
        Course saved = saveCourse("CMPT", "225");
        entityManager.clear();
        Instant firstFetchedAt = courseRepository.findById(saved.getId()).orElseThrow().getFetchedAt();

        // Wait a little so the second time is clearly later than the first
        Thread.sleep(10);

        Course course = courseRepository.findById(saved.getId()).orElseThrow();
        course.markFetched();
        courseRepository.saveAndFlush(course);
        entityManager.clear();

        Instant secondFetchedAt = courseRepository.findById(saved.getId()).orElseThrow().getFetchedAt();
        assertThat(secondFetchedAt).isAfter(firstFetchedAt);
    }
}
