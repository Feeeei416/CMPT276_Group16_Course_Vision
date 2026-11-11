package cmpt276.group16.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// Two requests changing the same student's courses at once, like a double click on Add course
// Not @Transactional: each change has to commit on its own, like two real requests, so the rows are deleted
// after each test instead of rolled back
@SpringBootTest
class SimultaneousChangesTest {

    @Autowired
    private TranscriptService transcriptService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TranscriptCourseRepository transcriptCourseRepository;

    private StudentProfile jack;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @BeforeEach
    void saveJack() {
        User user = userRepository.save(new User("simultaneous@example.com", "hash", "Jack", Role.STUDENT));
        jack = studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    @AfterEach
    void deleteJack() {
        pool.shutdown();
        transcriptCourseRepository.deleteAll(transcriptCourseRepository.findByStudentOrderByIdAsc(jack));
        studentProfileRepository.delete(jack);
        userRepository.delete(jack.getUser());
    }

    private static CourseInput cmpt225() {
        return new CourseInput("2024 Fall", "CMPT", "225", "Data Structures", new BigDecimal("3.00"), "B");
    }

    // The barrier holds both threads until both are ready, so the two changes really do overlap
    private List<Boolean> atOnce(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
        CyclicBarrier together = new CyclicBarrier(2);
        return pool.invokeAll(List.<Callable<Boolean>>of(
                () -> { together.await(); return first.call(); },
                () -> { together.await(); return second.call(); })).stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }

    @Test
    void addingTheSameCourseTwiceAtOnceSavesOne() throws Exception {
        List<Boolean> added = atOnce(() -> transcriptService.addByHand(jack, cmpt225()),
                () -> transcriptService.addByHand(jack, cmpt225()));

        assertThat(added).containsExactlyInAnyOrder(true, false);
        assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(jack)).hasSize(1);
    }

    @Test
    void editingTwoCoursesIntoTheSameOneAtOnceChangesOne() throws Exception {
        TranscriptCourse first = transcriptCourseRepository.save(new TranscriptCourse(jack, "2024 Fall", "CMPT",
                "120", "Some Course", new BigDecimal("3.00"), "A"));
        TranscriptCourse second = transcriptCourseRepository.save(new TranscriptCourse(jack, "2024 Fall", "CMPT",
                "125", "Some Course", new BigDecimal("3.00"), "A"));

        List<Boolean> edited = atOnce(() -> transcriptService.editByHand(first, cmpt225()),
                () -> transcriptService.editByHand(second, cmpt225()));

        assertThat(edited).containsExactlyInAnyOrder(true, false);
        // One of them became CMPT 225 and the other kept its number
        assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(jack))
                .extracting(TranscriptCourse::getNumber).containsOnlyOnce("225").hasSize(2);
    }
}
