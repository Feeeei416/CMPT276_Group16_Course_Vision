package cmpt276.group16.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

@DataJpaTest
class DataSeederTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private AdvisorAssignmentRepository advisorAssignmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Runs the seeder with a chosen password, then clears the cache so checks read the database
    private void runSeeder(String password) {
        new DataSeeder(userRepository, studentProfileRepository, advisorAssignmentRepository,
                passwordEncoder, password).run();
        entityManager.flush();
        entityManager.clear();
    }

    private User user(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    void addsEachDemoAccountWithRole() {
        runSeeder("test-password");

        assertThat(user("advisor@example.com").getRole()).isEqualTo(Role.ADVISOR);
        assertThat(user("faye@example.com").getRole()).isEqualTo(Role.IT_MANAGER);
        assertThat(user("madden@test.com").getRole()).isEqualTo(Role.STUDENT);
        assertThat(user("jack@test.com").getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void studentsGetTheirProgramAndCohort() {
        runSeeder("test-password");

        StudentProfile madden = studentProfileRepository.findById(user("madden@test.com").getId()).orElseThrow();
        assertThat(madden.getProgram()).isEqualTo(Program.COMPUTING_SCIENCE);
        assertThat(madden.getCohortYear()).isEqualTo(2024);

        StudentProfile jack = studentProfileRepository.findById(user("jack@test.com").getId()).orElseThrow();
        assertThat(jack.getProgram()).isEqualTo(Program.COMPUTER_ENGINEERING);
        assertThat(jack.getCohortYear()).isEqualTo(2023);
    }

    @Test
    void advisorIsAssignedToBothStudents() {
        runSeeder("test-password");

        Long advisorId = user("advisor@example.com").getId();
        assertThat(advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(
                advisorId, user("madden@test.com").getId())).isTrue();
        assertThat(advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(
                advisorId, user("jack@test.com").getId())).isTrue();
    }

    @Test
    void demoPasswordIsStoredHashed() {
        runSeeder("test-password");

        String storedHash = user("madden@test.com").getPasswordHash();
        assertThat(storedHash).isNotEqualTo("test-password");
        assertThat(passwordEncoder.matches("test-password", storedHash)).isTrue();
    }

    // Like restarting the app: the second run finds everything and adds nothing
    @Test
    void runningTwiceAddsNothingNew() {
        runSeeder("test-password");
        runSeeder("test-password");

        assertThat(userRepository.count()).isEqualTo(4);
        assertThat(studentProfileRepository.count()).isEqualTo(2);
        assertThat(advisorAssignmentRepository.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void addsNothingWithoutPassword(String password) {
        runSeeder(password);

        assertThat(userRepository.count()).isZero();
    }
}
