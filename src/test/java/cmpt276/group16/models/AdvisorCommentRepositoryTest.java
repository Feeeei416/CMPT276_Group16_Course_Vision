package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class AdvisorCommentRepositoryTest {

    private static final Instant NOV_24 = Instant.parse("2026-11-24T18:00:00Z");
    private static final Instant NOV_25 = Instant.parse("2026-11-25T18:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private AdvisorCommentRepository commentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User advisor;
    private StudentProfile jack;
    private StudentProfile finn;

    @BeforeEach
    void setUp() {
        advisor = userRepository.saveAndFlush(new User("advisor@example.com", "hash", "Alex", Role.ADVISOR));
        jack = saveStudent("jack@example.com", "Jack");
        finn = saveStudent("finn@example.com", "Finn");
    }

    private StudentProfile saveStudent(String email, String displayName) {
        User user = userRepository.saveAndFlush(new User(email, "hash", displayName, Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private AdvisorComment save(StudentProfile student, String body, Instant createdAt) {
        return commentRepository.saveAndFlush(new AdvisorComment(student, advisor, body, createdAt));
    }

    @Test
    void commentIsSavedWithItsAdvisorAndTime() {
        AdvisorComment saved = save(jack, "Take CMPT 225 in Spring.", NOV_25);
        entityManager.clear();

        AdvisorComment found = commentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getBody()).isEqualTo("Take CMPT 225 in Spring.");
        assertThat(found.getAdvisor().getDisplayName()).isEqualTo("Alex");
        assertThat(found.getStudent().getUser().getDisplayName()).isEqualTo("Jack");
        assertThat(found.getCreatedAt()).isEqualTo(NOV_25);
    }

    // A report shows only its own student's comments, the newest at the top
    @Test
    void findsOnlyThisStudentsCommentsNewestFirst() {
        save(jack, "Older", NOV_24);
        save(finn, "For Finn", NOV_25);
        save(jack, "Newer", NOV_25);
        entityManager.clear();

        assertThat(commentRepository.findByStudentOrderByCreatedAtDescIdDesc(jack))
                .extracting(AdvisorComment::getBody)
                .containsExactly("Newer", "Older");
    }

    @Test
    void commentsFromTheSameInstantShowTheLastOneSavedFirst() {
        save(jack, "First", NOV_25);
        save(jack, "Second", NOV_25);
        entityManager.clear();

        assertThat(commentRepository.findByStudentOrderByCreatedAtDescIdDesc(jack))
                .extracting(AdvisorComment::getBody)
                .containsExactly("Second", "First");
    }

    @Test
    void onlyAnAdvisorCanWriteAComment() {
        assertThatThrownBy(() -> new AdvisorComment(jack, finn.getUser(), "Hi", NOV_25))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
