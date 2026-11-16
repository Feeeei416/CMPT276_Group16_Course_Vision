package cmpt276.group16.controllers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.*;
import cmpt276.group16.services.NextSemesterScheduleService;

// Uses the whole app, so AuthInterceptor and the real database are part of every test.
// addFilters = false: Spring Session's filter would swap the test's session for a database one
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class NextSemesterScheduleControllerTest {

    // Replaces the real clock, so next semester is always 2027 Spring in these
    // tests
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-09T20:00:00Z"), ZoneId.of("America/Vancouver"));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentProfileRepository studentProfileRepository;
    @Autowired
    private TermRepository termRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseOfferingRepository courseOfferingRepository;
    @Autowired
    private SectionRepository sectionRepository;
    @Autowired
    private NextSemesterScheduleService scheduleService;

    private StudentProfile priya;
    private StudentProfile marcus;
    private CourseOffering cmpt225;
    private Section d100, d101;

    @BeforeEach
    void setUp() {
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");
        Term spring2027 = termRepository.save(new Term(2027, Season.SPRING));
        Course course = courseRepository.save(
                new Course("CMPT", "225", "Data Structures", new BigDecimal("3"), null, null, null));
        cmpt225 = courseOfferingRepository.save(new CourseOffering(course, spring2027, true));
        d100 = sectionRepository.save(new Section(cmpt225, "D100", SectionType.LEC, true, "1"));
        d101 = sectionRepository.save(new Section(cmpt225, "D101", SectionType.TUT, false, "1"));
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.save(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private MockHttpSession sessionFor(StudentProfile student) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginController.SESSION_USER, student.getUser());
        return session;
    }

    @Test
    void signedOutVisitorGoesToLogin() throws Exception {
        mockMvc.perform(get("/student/schedule/next-semester"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void emptyScheduleSaysNoCoursesYet() throws Exception {
        mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(priya)))
                .andExpect(status().isOk())
                .andExpect(view().name("student/next-semester-schedule"))
                .andExpect(content().string(containsString("No courses yet")));
    }

    // Each student only sees their own schedule
    @Test
    void doesntShowAnotherStudentsSchedule() throws Exception {
        scheduleService.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());

        mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(marcus)))
                .andExpect(content().string(containsString("No courses yet")))
                .andExpect(content().string(not(containsString("D100"))));
    }

    @Test
    void textThatIsntACourseCodeShowsAnError() throws Exception {
        mockMvc.perform(get("/student/schedule/next-semester").param("code", "225").session(sessionFor(priya)))
                .andExpect(content().string(containsString("Enter a course code like CMPT 225.")));
    }

        @Test
    void removesACourse() throws Exception {
    scheduleService.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());
    Long courseId = scheduleService.coursesFor(priya).get(0).getId();

    mockMvc.perform(post("/student/schedule/next-semester/courses/{id}/remove", courseId)
                    .session(sessionFor(priya)))
           .andExpect(redirectedUrl("/student/schedule/next-semester"));

    assertThat(scheduleService.coursesFor(priya)).isEmpty();
}

@Test
void showsTimesNotConfirmedWhenASectionHasNoTimes() throws Exception {
    scheduleService.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());

    mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(priya)))
           .andExpect(content().string(containsString("Times not confirmed")));
}
}