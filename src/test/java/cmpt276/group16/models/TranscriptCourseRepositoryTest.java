package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// Covers only the Iteration 2 additions (the course link and the source).
// The older transcript behaviour is already covered by TranscriptServiceTest and TranscriptControllerTest
@DataJpaTest
class TranscriptCourseRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TranscriptCourseRepository transcriptCourseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private Course saveCourse(String subject, String number) {
        return courseRepository.saveAndFlush(new Course(subject, number, "Some Course",
                new BigDecimal("3.00"), null, null, null));
    }

    private TranscriptCourse newTranscriptCourse(StudentProfile student, CourseSource source) {
        return new TranscriptCourse(student, "2024 Fall", "CMPT", "225", "Data Structures",
                new BigDecimal("3.00"), "A", source);
    }

    // A row made from an uploaded transcript keeps its source, and starts without a link to the courses table
    @Test
    void savesSourceAndStartsWithoutCourseLink() {
        StudentProfile student = saveStudent("priya@example.com");
        TranscriptCourse saved = transcriptCourseRepository.saveAndFlush(
                newTranscriptCourse(student, CourseSource.PDF));
        entityManager.clear();

        TranscriptCourse found = transcriptCourseRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSource()).isEqualTo(CourseSource.PDF);
        assertThat(found.getCourse()).isNull();
    }

    // A manually added row keeps the MANUAL source
    @Test
    void savesManualSource() {
        StudentProfile student = saveStudent("priya@example.com");
        TranscriptCourse saved = transcriptCourseRepository.saveAndFlush(
                newTranscriptCourse(student, CourseSource.MANUAL));
        entityManager.clear();

        assertThat(transcriptCourseRepository.findById(saved.getId()).orElseThrow().getSource())
            .isEqualTo(CourseSource.MANUAL);
    }

    // Rows saved before Iteration 2 have no source, so a missing source must still save and load
    @Test
    void allowsMissingSourceForOlderRows() {
        StudentProfile student = saveStudent("priya@example.com");
        TranscriptCourse saved = transcriptCourseRepository.saveAndFlush(newTranscriptCourse(student, null));
        entityManager.clear();

        assertThat(transcriptCourseRepository.findById(saved.getId()).orElseThrow().getSource()).isNull();
    }

    // A transcript row can be linked to its course later, and the link is saved
    @Test
    void linksTranscriptRowToCourse() {
        StudentProfile student = saveStudent("priya@example.com");
        Course cmpt225 = saveCourse("CMPT", "225");
        TranscriptCourse saved = transcriptCourseRepository.saveAndFlush(
                newTranscriptCourse(student, CourseSource.PDF));
        entityManager.clear();

        TranscriptCourse row = transcriptCourseRepository.findById(saved.getId()).orElseThrow();
        row.setCourse(cmpt225);
        transcriptCourseRepository.saveAndFlush(row);
        entityManager.clear();

        TranscriptCourse found = transcriptCourseRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCourse().getId()).isEqualTo(cmpt225.getId());
        assertThat(found.getCourse().getNumber()).isEqualTo("225");
    }

    // Linking keeps what the transcript originally said, even if the course's own title is different
    @Test
    void linkingKeepsTranscriptDetails() {
        StudentProfile student = saveStudent("priya@example.com");
        Course cmpt225 = saveCourse("CMPT", "225");
        TranscriptCourse row = newTranscriptCourse(student, CourseSource.PDF);
        row.setCourse(cmpt225);
        TranscriptCourse saved = transcriptCourseRepository.saveAndFlush(row);
        entityManager.clear();

        TranscriptCourse found = transcriptCourseRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Data Structures");
        assertThat(found.getCourse().getTitle()).isEqualTo("Some Course");
        assertThat(found.getGrade()).isEqualTo("A");
        assertThat(found.getTerm()).isEqualTo("2024 Fall");
    }

    // Many students can take the same course, so many transcript rows can point at one course
    @Test
    void manyTranscriptRowsCanLinkToSameCourse() {
        StudentProfile priya = saveStudent("priya@example.com");
        StudentProfile marcus = saveStudent("marcus@example.com");
        Course cmpt225 = saveCourse("CMPT", "225");
        TranscriptCourse priyaRow = newTranscriptCourse(priya, CourseSource.PDF);
        priyaRow.setCourse(cmpt225);
        transcriptCourseRepository.saveAndFlush(priyaRow);
        TranscriptCourse marcusRow = newTranscriptCourse(marcus, CourseSource.PDF);
        marcusRow.setCourse(cmpt225);
        transcriptCourseRepository.saveAndFlush(marcusRow);

        assertThat(transcriptCourseRepository.count()).isEqualTo(2);
    }

    // Replacing a student's transcript removes their rows but leaves the shared course in place
    @Test
    void deletingStudentRowsKeepsCourse() {
        StudentProfile student = saveStudent("priya@example.com");
        Course cmpt225 = saveCourse("CMPT", "225");
        TranscriptCourse row = newTranscriptCourse(student, CourseSource.PDF);
        row.setCourse(cmpt225);
        transcriptCourseRepository.saveAndFlush(row);

        transcriptCourseRepository.deleteByStudent(student);
        transcriptCourseRepository.flush();
        entityManager.clear();

        assertThat(transcriptCourseRepository.countByStudent(student)).isZero();
        assertThat(courseRepository.findById(cmpt225.getId())).isPresent();
    }
}