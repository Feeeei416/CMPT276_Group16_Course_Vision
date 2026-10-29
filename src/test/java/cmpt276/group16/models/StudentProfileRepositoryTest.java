package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class StudentProfileRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User saveStudent() {
        return userRepository.saveAndFlush(new User("priya@example.com", "hash", "Priya", Role.STUDENT));
    }

    // Runs once per program: the profile shares the student's id, and the program is stored as its name
    @ParameterizedTest
    @EnumSource(Program.class)
    void profileIsFoundByTheStudentsUserId(Program program) {
        User student = saveStudent();
        studentProfileRepository.saveAndFlush(new StudentProfile(student, program, 2024));
        entityManager.clear();

        StudentProfile found = studentProfileRepository.findById(student.getId()).orElseThrow();

        assertThat(found.getUserId()).isEqualTo(student.getId());
        assertThat(found.getUser().getEmail()).isEqualTo("priya@example.com");
        assertThat(found.getProgram()).isEqualTo(program);
        assertThat(found.getCohortYear()).isEqualTo(2024);

        Object storedProgram = entityManager.getEntityManager()
                .createNativeQuery("select program from student_profiles where user_id = ?1")
                .setParameter(1, student.getId())
                .getSingleResult();
        assertThat(storedProgram.toString()).isEqualTo(program.name());
    }

    @Test
    void userWithoutProfileIsNotFound() {
        User advisor = userRepository.saveAndFlush(new User("advisor@example.com", "hash", "Advisor", Role.ADVISOR));

        assertThat(studentProfileRepository.findById(advisor.getId())).isEmpty();
        assertThat(studentProfileRepository.existsById(advisor.getId())).isFalse();
    }

    @Test
    void onlyOneProfilePerStudent() {
        User student = saveStudent();
        studentProfileRepository.saveAndFlush(new StudentProfile(student, Program.COMPUTING_SCIENCE, 2024));
        entityManager.clear();
        User reloaded = userRepository.findById(student.getId()).orElseThrow();

        assertThatThrownBy(() -> studentProfileRepository.saveAndFlush(
                new StudentProfile(reloaded, Program.COMPUTER_ENGINEERING, 2023)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Advisors and IT managers can't be given a student profile
    @ParameterizedTest
    @EnumSource(value = Role.class, names = "STUDENT", mode = EnumSource.Mode.EXCLUDE)
    void rejectsProfileForNonStudent(Role role) {
        User staff = userRepository.saveAndFlush(new User("staff@example.com", "hash", "Staff", role));

        assertThatThrownBy(() -> new StudentProfile(staff, Program.COMPUTING_SCIENCE, 2024))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {2023, 2024})
    void acceptsSupportedCohortYears(int year) {
        User student = saveStudent();
        studentProfileRepository.saveAndFlush(new StudentProfile(student, Program.COMPUTING_SCIENCE, year));
        entityManager.clear();

        assertThat(studentProfileRepository.findById(student.getId()).orElseThrow().getCohortYear()).isEqualTo(year);
    }

    @ParameterizedTest
    @ValueSource(ints = {2022, 2025})
    void rejectsUnsupportedCohortYears(int year) {
        User student = saveStudent();

        assertThatThrownBy(() -> new StudentProfile(student, Program.COMPUTING_SCIENCE, year))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
