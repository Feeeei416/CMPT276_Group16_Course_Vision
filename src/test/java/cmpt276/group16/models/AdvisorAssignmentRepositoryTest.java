package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class AdvisorAssignmentRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private AdvisorAssignmentRepository advisorAssignmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User saveAdvisor(String email) {
        return userRepository.saveAndFlush(new User(email, "hash", "Advisor", Role.ADVISOR));
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    @Test
    void assignmentIsSavedAndFound() {
        User advisor = saveAdvisor("advisor@example.com");
        StudentProfile student = saveStudent("priya@example.com");
        AdvisorAssignment saved = advisorAssignmentRepository.saveAndFlush(new AdvisorAssignment(advisor, student));
        entityManager.clear();

        assertThat(advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(advisor.getId(), student.getUserId())).isTrue();

        AdvisorAssignment found = advisorAssignmentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getAdvisor().getEmail()).isEqualTo("advisor@example.com");
        assertThat(found.getStudent().getUser().getEmail()).isEqualTo("priya@example.com");
        assertThat(found.getAssignedAt()).isNotNull();
    }

    @Test
    void otherAdvisorIsNotAssigned() {
        User advisor = saveAdvisor("advisor@example.com");
        User otherAdvisor = saveAdvisor("other@example.com");
        StudentProfile student = saveStudent("priya@example.com");
        advisorAssignmentRepository.saveAndFlush(new AdvisorAssignment(advisor, student));

        assertThat(advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(otherAdvisor.getId(), student.getUserId())).isFalse();
    }

    @Test
    void advisorCanSupportSeveralStudents() {
        User advisor = saveAdvisor("advisor@example.com");
        StudentProfile priya = saveStudent("priya@example.com");
        StudentProfile marcus = saveStudent("marcus@example.com");
        advisorAssignmentRepository.saveAndFlush(new AdvisorAssignment(advisor, priya));
        advisorAssignmentRepository.saveAndFlush(new AdvisorAssignment(advisor, marcus));

        assertThat(advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(advisor.getId(), priya.getUserId())).isTrue();
        assertThat(advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(advisor.getId(), marcus.getUserId())).isTrue();
    }

    @Test
    void rejectsDuplicateAssignment() {
        User advisor = saveAdvisor("advisor@example.com");
        StudentProfile student = saveStudent("priya@example.com");
        advisorAssignmentRepository.saveAndFlush(new AdvisorAssignment(advisor, student));

        assertThatThrownBy(() -> advisorAssignmentRepository.saveAndFlush(new AdvisorAssignment(advisor, student)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Students and IT managers can't be assigned as someone's advisor
    @ParameterizedTest
    @EnumSource(value = Role.class, names = "ADVISOR", mode = EnumSource.Mode.EXCLUDE)
    void rejectsNonAdvisor(Role role) {
        User notAdvisor = userRepository.saveAndFlush(new User("someone@example.com", "hash", "Someone", role));
        StudentProfile student = saveStudent("priya@example.com");

        assertThatThrownBy(() -> new AdvisorAssignment(notAdvisor, student))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
