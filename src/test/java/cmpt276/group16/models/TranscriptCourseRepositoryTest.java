package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// Covers only the Iteration 2 addition (the link to the courses table).
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

    private TranscriptCourse newTranscriptCourse(StudentProfile student) {
        return new TranscriptCourse(student, "2024 Fall", "CMPT", "225", "Data Structures",
                new BigDecimal("3.00"), "A");
    }

    // A new transcript row starts without a link, since the matching course may not be fetched yet
    @Test
    void startsWithoutCourseLink() {
        StudentProfile student = saveStudent("priya@example.com");
        TranscriptCourse saved = transcriptCourseRepository.saveAndFlush(newTranscriptCourse(student));
        entityManager.clear();

        assertThat(transcriptCourseRepository.findById(saved.getId()).orElseThrow().getCourse()).isNull();
    }

    // A transcript row can be linked to its course later, and the link is saved
    @Test
    void linksTranscriptRowToCourse() {
        StudentProfile student = saveStudent("priya@example.com");
        Course cmpt225 = saveCourse("CMPT", "225");
        TranscriptCourse saved = transcriptCourseRepository.saveAndFlush(newTranscriptCourse(student));
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
        TranscriptCourse row = newTranscriptCourse(student);
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
        TranscriptCourse priyaRow = newTranscriptCourse(priya);
        priyaRow.setCourse(cmpt225);
        transcriptCourseRepository.saveAndFlush(priyaRow);
        TranscriptCourse marcusRow = newTranscriptCourse(marcus);
        marcusRow.setCourse(cmpt225);
        transcriptCourseRepository.saveAndFlush(marcusRow);

        assertThat(transcriptCourseRepository.count()).isEqualTo(2);
    }
}