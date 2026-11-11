package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class CourseDesignationRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseDesignationRepository courseDesignationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Course saveCourse(String subject, String number) {
        return courseRepository.saveAndFlush(new Course(subject, number, "Some Course",
                new BigDecimal("3.00"), null, null, null));
    }

    private CourseDesignation saveDesignation(Course course, Designation designation) {
        return courseDesignationRepository.saveAndFlush(new CourseDesignation(course, designation));
    }

    // A saved designation can be loaded back by its course and keeps both parts of its id
    @Test
    void designationIsSavedAndFoundByCourse() {
        Course cmpt105w = saveCourse("CMPT", "105W");
        saveDesignation(cmpt105w, Designation.W);
        entityManager.clear();

        assertThat(courseDesignationRepository.findByCourseId(cmpt105w.getId()))
            .singleElement()
            .satisfies(found -> {
                assertThat(found.getCourse().getNumber()).isEqualTo("105W");
                assertThat(found.getDesignation()).isEqualTo(Designation.W);
                assertThat(found.getId().getCourseId()).isEqualTo(cmpt105w.getId());
                assertThat(found.getId().getDesignation()).isEqualTo(Designation.W);
            });
    }

    // A designation can be looked up directly by its composite id
    @Test
    void designationIsFoundByCompositeId() {
        Course cmpt105w = saveCourse("CMPT", "105W");
        saveDesignation(cmpt105w, Designation.W);
        entityManager.clear();

        assertThat(courseDesignationRepository.findById(new CourseDesignationId(cmpt105w.getId(), Designation.W)))
            .isPresent();
        assertThat(courseDesignationRepository.findById(new CourseDesignationId(cmpt105w.getId(), Designation.Q)))
            .isEmpty();
    }

    // One course can count for several designations, for example both Q and B-Sci
    @Test
    void courseCanHaveSeveralDesignations() {
        Course macm101 = saveCourse("MACM", "101");
        saveDesignation(macm101, Designation.Q);
        saveDesignation(macm101, Designation.B_SCI);
        entityManager.clear();

        assertThat(courseDesignationRepository.findByCourseId(macm101.getId()))
            .extracting(CourseDesignation::getDesignation)
            .containsExactlyInAnyOrder(Designation.Q, Designation.B_SCI);
    }

    // Looking up one course only returns that course's designations
    @Test
    void findsOnlyThisCoursesDesignations() {
        Course cmpt105w = saveCourse("CMPT", "105W");
        Course macm101 = saveCourse("MACM", "101");
        saveDesignation(cmpt105w, Designation.W);
        saveDesignation(macm101, Designation.Q);
        entityManager.clear();

        assertThat(courseDesignationRepository.findByCourseId(cmpt105w.getId()))
            .extracting(CourseDesignation::getDesignation)
            .containsExactly(Designation.W);
    }

    // A course with no designations gives an empty list instead of an error
    @Test
    void findReturnsEmptyForCourseWithoutDesignations() {
        Course cmpt225 = saveCourse("CMPT", "225");

        assertThat(courseDesignationRepository.findByCourseId(cmpt225.getId())).isEmpty();
    }

    // exists is true only when both the course and the designation match
    @Test
    void existsOnlyForSavedDesignation() {
        Course cmpt105w = saveCourse("CMPT", "105W");
        Course cmpt225 = saveCourse("CMPT", "225");
        saveDesignation(cmpt105w, Designation.W);

        assertThat(courseDesignationRepository.existsByCourseIdAndIdDesignation(cmpt105w.getId(), Designation.W)).isTrue();
        assertThat(courseDesignationRepository.existsByCourseIdAndIdDesignation(cmpt105w.getId(), Designation.Q)).isFalse();
        assertThat(courseDesignationRepository.existsByCourseIdAndIdDesignation(cmpt225.getId(), Designation.W)).isFalse();
    }

    // The same designation can belong to many courses
    @Test
    void allowsSameDesignationOnDifferentCourses() {
        Course cmpt105w = saveCourse("CMPT", "105W");
        Course cmpt376w = saveCourse("CMPT", "376W");
        saveDesignation(cmpt105w, Designation.W);
        saveDesignation(cmpt376w, Designation.W);

        assertThat(courseDesignationRepository.count()).isEqualTo(2);
    }

    // Course and designation together are the primary key, so saving the same pair again
    // updates the existing row instead of adding a second one
    @Test
    void savingSameDesignationTwiceKeepsOneRow() {
        Course cmpt105w = saveCourse("CMPT", "105W");
        saveDesignation(cmpt105w, Designation.W);
        saveDesignation(cmpt105w, Designation.W);

        assertThat(courseDesignationRepository.count()).isEqualTo(1);
    }

    // Removing a designation leaves the course and its other designations in place
    @Test
    void deletingDesignationKeepsCourseAndOtherDesignations() {
        Course macm101 = saveCourse("MACM", "101");
        saveDesignation(macm101, Designation.Q);
        saveDesignation(macm101, Designation.B_SCI);
        courseDesignationRepository.deleteById(new CourseDesignationId(macm101.getId(), Designation.Q));
        courseDesignationRepository.flush();
        entityManager.clear();

        assertThat(courseRepository.findById(macm101.getId())).isPresent();
        assertThat(courseDesignationRepository.findByCourseId(macm101.getId()))
            .extracting(CourseDesignation::getDesignation)
            .containsExactly(Designation.B_SCI);
    }

    // Two ids with the same course and designation are equal, which Hibernate needs to match rows
    @Test
    void idsWithSameCourseAndDesignationAreEqual() {
        CourseDesignationId first = new CourseDesignationId(1L, Designation.W);
        CourseDesignationId second = new CourseDesignationId(1L, Designation.W);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    // Ids that differ in either the course or the designation are not equal
    @Test
    void idsWithDifferentCourseOrDesignationAreNotEqual() {
        CourseDesignationId id = new CourseDesignationId(1L, Designation.W);

        assertThat(id).isNotEqualTo(new CourseDesignationId(2L, Designation.W));
        assertThat(id).isNotEqualTo(new CourseDesignationId(1L, Designation.Q));
    }
}