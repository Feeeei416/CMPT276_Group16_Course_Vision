package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import cmpt276.group16.models.AdvisorComment;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// #72: @DataJpaTest only sets up the repositories, so the service and a fixed clock are added with @Import
@DataJpaTest
@Import({AdvisorCommentService.class, AdvisorCommentServiceTest.FixedClock.class})
class AdvisorCommentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T20:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneId.of("America/Vancouver"));
        }
    }

    @Autowired
    private AdvisorCommentService commentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    private User advisor;
    private StudentProfile jack;
    private StudentProfile finn;

    @BeforeEach
    void setUp() {
        advisor = userRepository.save(new User("advisor@example.com", "hash", "Alex", Role.ADVISOR));
        jack = saveStudent("jack@example.com", "Jack");
        finn = saveStudent("finn@example.com", "Finn");
    }

    private StudentProfile saveStudent(String email, String displayName) {
        User user = userRepository.save(new User(email, "hash", displayName, Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    @Test
    void commentIsSavedWithTheClocksTime() {
        assertThat(commentService.add(advisor, jack, "Take CMPT 225 in Spring.")).isEmpty();

        assertThat(commentService.commentsFor(jack)).singleElement().satisfies(comment -> {
            assertThat(comment.getBody()).isEqualTo("Take CMPT 225 in Spring.");
            assertThat(comment.getAdvisor()).isEqualTo(advisor);
            assertThat(comment.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void commentsForListsOnlyThatStudentsComments() {
        commentService.add(advisor, jack, "For Jack");
        commentService.add(advisor, finn, "For Finn");

        assertThat(commentService.commentsFor(jack)).extracting(AdvisorComment::getBody).containsExactly("For Jack");
    }

    @Test
    void emptyCommentIsntSaved() {
        assertThat(commentService.add(advisor, jack, " \n\t ")).containsExactly(AdvisorCommentService.EMPTY);
        assertThat(commentService.add(advisor, jack, null)).containsExactly(AdvisorCommentService.EMPTY);

        assertThat(commentService.commentsFor(jack)).isEmpty();
    }

    @Test
    void commentOverTheLimitIsntSaved() {
        assertThat(commentService.add(advisor, jack, "a".repeat(2001)))
                .containsExactly("A comment can be at most 2000 characters (this one has 2001).");

        assertThat(commentService.commentsFor(jack)).isEmpty();
    }

    @Test
    void commentRightAtTheLimitIsSaved() {
        assertThat(commentService.add(advisor, jack, "a".repeat(2000))).isEmpty();

        assertThat(commentService.commentsFor(jack)).hasSize(1);
    }

    // Spaces and blank lines at the ends are dropped, but the ones inside are kept
    @Test
    void spacesAtTheEndsAreDropped() {
        assertThat(commentService.add(advisor, jack, "\n  First line\n\nSecond line  \n")).isEmpty();

        assertThat(commentService.commentsFor(jack)).singleElement()
                .extracting(AdvisorComment::getBody)
                .isEqualTo("First line\n\nSecond line");
    }
}
