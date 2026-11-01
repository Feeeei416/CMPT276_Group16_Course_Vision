package cmpt276.group16.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// @DataJpaTest will only set up repos, so the service is added with @Import
@DataJpaTest
@Import(TranscriptService.class)
class TranscriptServiceTest {

    @Autowired
    private TranscriptService transcriptService;

    @Autowired
    private TranscriptCourseRepository transcriptCourseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TestEntityManager entityManager;

    private StudentProfile student;

    private static final ParsedCourse GRADED = new ParsedCourse("2025 Fall", "CMPT", "120",
            "Intro.Cmpt.Sci/Programming I", new BigDecimal("3.00"), "A-");
    private static final ParsedCourse IN_PROGRESS = new ParsedCourse("2026 Fall", "CMPT", "105W",
            "Social Issues&Comm Strategies", new BigDecimal("3.00"), null);

    @BeforeEach
    void saveStudent() {
        student = saveStudent("priya@example.com");
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    // saves and then clears Hibernate's cache so the read below really comes from the database
    private List<TranscriptCourse> replaceAndReload(StudentProfile profile, List<ParsedCourse> courses) {
        transcriptService.replaceCourses(profile, courses);
        entityManager.flush();
        entityManager.clear();
        return transcriptCourseRepository.findByStudentOrderByIdAsc(profile);
    }

    @Test
    void savesEveryFieldOfEachCourse() {
        List<TranscriptCourse> saved = replaceAndReload(student, List.of(GRADED, IN_PROGRESS));

        assertThat(saved).hasSize(2);
        TranscriptCourse first = saved.get(0);
        assertThat(first.getStudent().getUserId()).isEqualTo(student.getUserId());
        assertThat(first.getTerm()).isEqualTo("2025 Fall");
        assertThat(first.getSubject()).isEqualTo("CMPT");
        assertThat(first.getNumber()).isEqualTo("120");
        assertThat(first.getTitle()).isEqualTo("Intro.Cmpt.Sci/Programming I");
        assertThat(first.getUnits()).isEqualByComparingTo("3.00");
        assertThat(first.getGrade()).isEqualTo("A-");
    }

    @Test
    void inProgressCourseIsSavedWithNoGrade() {
        TranscriptCourse saved = replaceAndReload(student, List.of(IN_PROGRESS)).get(0);

        assertThat(saved.getGrade()).isNull();
        assertThat(saved.isInProgress()).isTrue();
    }

    @Test
    void coursesComeBackInTranscriptOrder() {
        List<TranscriptCourse> saved = replaceAndReload(student, List.of(IN_PROGRESS, GRADED));

        assertThat(saved).extracting(TranscriptCourse::getNumber).containsExactly("105W", "120");
    }

    // Uploading a newer transcript should not leave duplicates of the old courses
    @Test
    void replacingRemovesTheOldCourses() {
        replaceAndReload(student, List.of(GRADED, IN_PROGRESS));

        List<TranscriptCourse> saved = replaceAndReload(student, List.of(GRADED));

        assertThat(saved).extracting(TranscriptCourse::getNumber).containsExactly("120");
    }

    @Test
    void otherStudentsCoursesAreNotTouched() {
        StudentProfile other = saveStudent("sam@example.com");
        replaceAndReload(other, List.of(GRADED));

        replaceAndReload(student, List.of(IN_PROGRESS));

        assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(other))
                .extracting(TranscriptCourse::getNumber).containsExactly("120");
    }
}
