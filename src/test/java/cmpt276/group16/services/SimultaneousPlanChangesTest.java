package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.PlanRepository;
import cmpt276.group16.models.PlanTermRepository;
import cmpt276.group16.models.PlannedCourseRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// Two requests changing the same student's plan at once, like a double click on Add term.
// Not @Transactional: each change has to commit on its own, like two real requests, so the rows are deleted
// after each test instead of rolled back. Same approach as SimultaneousChangesTest for My courses
@SpringBootTest
class SimultaneousPlanChangesTest {

    // Next semester is always 2027 Spring, so 2027 Fall can be planned whenever this runs
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-09T20:00:00Z"), ZoneId.of("America/Vancouver"));
        }
    }

    @Autowired
    private PlanService planService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanTermRepository planTermRepository;

    @Autowired
    private PlannedCourseRepository plannedCourseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private CourseRepository courseRepository;

    private StudentProfile priya;
    private Course macm101;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @BeforeEach
    void savePriya() {
        User user = userRepository.save(new User("simultaneous.plan@example.com", "hash", "Priya", Role.STUDENT));
        priya = studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
        macm101 = courseRepository.save(new Course("MACM", "101", "Discrete Mathematics I", new BigDecimal("3.00"),
                null, null, null));
    }

    // Children first, since each row points at the one before it
    @AfterEach
    void deleteEverything() {
        pool.shutdown();
        planRepository.findByStudentAndMainTrue(priya).ifPresent(plan -> {
            plannedCourseRepository.deleteAll(plannedCourseRepository.findByTermPlan(plan));
            planTermRepository.deleteAll(planTermRepository.findByPlan(plan));
            planRepository.delete(plan);
        });
        termRepository.findByTermYearAndSeason(2027, Season.FALL).ifPresent(termRepository::delete);
        courseRepository.delete(macm101);
        studentProfileRepository.delete(priya);
        userRepository.delete(priya.getUser());
    }

    // The barrier holds both threads until both are ready, so the two changes really do overlap
    private List<List<String>> atOnce(Callable<List<String>> first, Callable<List<String>> second) throws Exception {
        CyclicBarrier together = new CyclicBarrier(2);
        return pool.invokeAll(List.<Callable<List<String>>>of(
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

    // Without the lock, both would create a "My plan" and the page would break for this student from then on
    @Test
    void addingTheFirstTermTwiceAtOnceMakesOnePlanAndOneTerm() throws Exception {
        List<List<String>> results = atOnce(() -> planService.addTerm(priya, 2027, "FALL").problems(),
                () -> planService.addTerm(priya, 2027, "FALL").problems());

        assertThat(results).containsExactlyInAnyOrder(List.of(), List.of("2027 Fall is already in your plan."));
        assertThat(planService.termPlans(priya)).hasSize(1);
    }

    @Test
    void addingTheSameCourseTwiceAtOnceSavesOne() throws Exception {
        planService.addTerm(priya, 2027, "FALL");
        Long fall = planService.termPlans(priya).get(0).term().getId();

        List<List<String>> results = atOnce(() -> planService.addCourse(priya, fall, "MACM 101").problems(),
                () -> planService.addCourse(priya, fall, "MACM 101").problems());

        assertThat(results).containsExactlyInAnyOrder(List.of(), List.of("MACM 101 is already in 2027 Fall."));
        assertThat(planService.termPlans(priya).get(0).courses()).hasSize(1);
    }
}
