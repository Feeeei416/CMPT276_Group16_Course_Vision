package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;


import cmpt276.group16.models.*;

// @DataJpaTest only loads repositories, so the service is built by hand with a fixed date
@DataJpaTest
public class NextSemesterScheduleServiceTest {
     private static final Clock NOV_9_2026 =
            Clock.fixed(Instant.parse("2026-11-09T20:00:00Z"), ZoneId.of("America/Vancouver"));

    @Autowired private UserRepository userRepository;
    @Autowired private StudentProfileRepository studentProfileRepository;
    @Autowired private TermRepository termRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseOfferingRepository courseOfferingRepository;
    @Autowired private SectionRepository sectionRepository;
    @Autowired private NextSemesterScheduleRepository scheduleRepository;
    @Autowired private ScheduledCourseRepository scheduledCourseRepository;
    

    private NextSemesterScheduleService service;
    private StudentProfile priya;
    private StudentProfile marcus;
    private CourseOffering cmpt225;
    private Section d100, d101, d200, d201;

    @BeforeEach
    void setUp() {
        service = new NextSemesterScheduleService(scheduleRepository, scheduledCourseRepository,
        sectionRepository, termRepository, courseOfferingRepository, studentProfileRepository, NOV_9_2026);
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");

        // CMPT 225 in 2027 Spring: two lectures, each with its own tutorial
        Term spring2027 = termRepository.save(new Term(2027, Season.SPRING));
        Course course = courseRepository.save(
                new Course("CMPT", "225", "Data Structures", new BigDecimal("3"), null, null, null));
        cmpt225 = courseOfferingRepository.save(new CourseOffering(course, spring2027, true));
        d100 = sectionRepository.save(new Section(cmpt225, "D100", SectionType.LEC, true, "1"));
        d101 = sectionRepository.save(new Section(cmpt225, "D101", SectionType.TUT, false, "1"));
        d200 = sectionRepository.save(new Section(cmpt225, "D200", SectionType.LEC, true, "2"));
        d201 = sectionRepository.save(new Section(cmpt225, "D201", SectionType.TUT, false, "2"));
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    // Test 1: the happy path
    @Test
    void addsALectureAndItsTutorial() {
        assertThat(service.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId())).isEmpty();
        assertThat(service.coursesFor(priya)).hasSize(1);
    }

    // Test 2: D201 belongs to lecture D200, so it can't be paired with D100
    @Test
    void rejectsATutorialFromAnotherLecture() {
        assertThat(service.addCourse(priya, cmpt225.getId(), d100.getId(), d201.getId()))
                .containsExactly("That lab or tutorial doesn't go with the lecture you chose.");
        assertThat(service.coursesFor(priya)).isEmpty();
    }
    
    // Test 3: a course can only be in the schedule once, even with different sections
    @Test
    void rejectsTheSameCourseTwice() {
        service.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());

        assertThat(service.addCourse(priya, cmpt225.getId(), d200.getId(), d201.getId()))
                .containsExactly("That course is already in your schedule.");
        assertThat(service.coursesFor(priya)).hasSize(1);
    }

    // Test 4: Marcus uses Priya's course id; nothing of hers changes
    @Test
    void cantRemoveAnotherStudentsCourse() {
        service.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());
        Long priyasCourseId = service.coursesFor(priya).get(0).getId();

        assertThat(service.removeCourse(marcus, priyasCourseId))
                .containsExactly("That course isn't in your schedule.");
        assertThat(service.coursesFor(priya)).hasSize(1);
    }

    // Test 5: in November 2026, next semester is 2027 Spring
    @Test
    void nextSemesterInNovemberIsSpringOfNextYear() {
        Term term = service.nextSemesterTerm().orElseThrow();

        assertThat(term.getTermYear()).isEqualTo(2027);
        assertThat(term.getSeason()).isEqualTo(Season.SPRING);
    }

    // Test 6: D100 has a tutorial, so it can't be added without one
    @Test
    void requiresATutorialWhenTheLectureHasOne() {
        assertThat(service.addCourse(priya, cmpt225.getId(), d100.getId(), null))
                .containsExactly("Choose a lab or tutorial for this lecture.");
        assertThat(service.coursesFor(priya)).isEmpty();
    }
}
