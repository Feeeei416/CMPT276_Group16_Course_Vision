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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

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
    @Autowired
    private NextSemesterScheduleRepository scheduleRepository;

    private StudentProfile priya;
    private StudentProfile marcus;
    private Term spring2027;
    private CourseOffering cmpt225;
    private Section d100, d101;

    @BeforeEach
    void setUp() {
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");
        spring2027 = termRepository.save(new Term(2027, Season.SPRING));
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
        SessionUser.signIn(session, student.getUser());
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
                .andExpect(redirectedUrl("/student/schedule/next-semester"))
                .andExpect(flash().attribute("message", "Course removed."));

        assertThat(scheduleService.coursesFor(priya)).isEmpty();
    }

    @Test
    void showsTimesNotConfirmedWhenASectionHasNoTimes() throws Exception {
        scheduleService.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());

        mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(priya)))
                .andExpect(content().string(containsString("Times not confirmed")));
    }

    // The part of the page inside one dropdown, from its id to its closing tag
    private static String dropdown(String html, String id) {
        int start = html.indexOf("id=\"" + id + "\"");
        return html.substring(start, html.indexOf("</select>", start));
    }

    // Choosing a course fills both dropdowns, and each option sends its section's
    // id
    @Test
    void choosingACourseListsItsSections() throws Exception {
        String html = mockMvc.perform(get("/student/schedule/next-semester").param("code", "CMPT 225")
                .session(sessionFor(priya))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(dropdown(html, "lecture-select")).contains("value=\"" + d100.getId() + "\"", "D100");
        assertThat(dropdown(html, "attached-select")).contains("value=\"" + d101.getId() + "\"", "D101");
    }

    // #76: Add a course suggests next semester's courses as the student types, from JSON in the page
    @Test
    void addACourseCarriesNextSemestersCoursesForSuggestions() throws Exception {
        String html = mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(priya)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-course-search=\"course-options\"", "data-fill=\"submit\"",
                "\"code\":\"CMPT 225\"", "/js/course-search.js");
    }

    // A tutorial SFU has cancelled isn't offered
    @Test
    void cancelledSectionsArentListed() throws Exception {
        Section d102 = new Section(cmpt225, "D102", SectionType.TUT, false, "1");
        d102.setOffered(false);
        sectionRepository.save(d102);

        String html = mockMvc.perform(get("/student/schedule/next-semester").param("code", "CMPT 225")
                .session(sessionFor(priya))).andReturn().getResponse().getContentAsString();

        assertThat(dropdown(html, "attached-select")).contains("D101").doesNotContain("D102");
    }

    @Test
    void addsACourseThroughTheForm() throws Exception {
        mockMvc.perform(post("/student/schedule/next-semester/courses").session(sessionFor(priya))
                .param("offeringId", cmpt225.getId().toString())
                .param("primarySectionId", d100.getId().toString())
                .param("attachedSectionId", d101.getId().toString()))
                .andExpect(redirectedUrl("/student/schedule/next-semester"))
                .andExpect(flash().attribute("message", "Course added."));

        assertThat(scheduleService.coursesFor(priya)).hasSize(1);
    }

    // The weekly grid puts each class at its day and time
    @Test
    void showsClassesOnTheWeeklyGrid() throws Exception {
        d100.replaceMeetings(
                List.of(new SectionMeeting(DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20), null)));
        sectionRepository.save(d100);
        scheduleService.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());

        mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(priya)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"timetable\"")))
                .andExpect(content().string(containsString("grid-column: 2; grid-row: 17 / 22;")))
                .andExpect(content().string(not(containsString(">Sat<"))));
    }

    // Saturday only gets a column when a class meets then
    @Test
    void showsSaturdayWhenAClassMeetsThen() throws Exception {
        d100.replaceMeetings(
                List.of(new SectionMeeting(DayOfWeek.SATURDAY, LocalTime.of(9, 30), LocalTime.of(12, 20), null)));
        sectionRepository.save(d100);
        scheduleService.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());

        mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(priya)))
                .andExpect(content().string(containsString(">Sat<")))
                .andExpect(content().string(not(containsString(">Sun<"))));
        }
    private NextSemesterSchedule saveSchedule(StudentProfile student, String name, boolean main) {
        return scheduleRepository.save(new NextSemesterSchedule(student, spring2027, name, main));
    }

    @Test
    void mainScheduleShowsItsNameAndBadge() throws Exception {
        saveSchedule(priya, "Morning labs", true);

        mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(priya)))
                .andExpect(content().string(containsString("Morning labs")))
                .andExpect(content().string(containsString("Main schedule")))
                .andExpect(content().string(not(containsString("This isn't your main schedule"))));
    }

    // #61: /student/schedules/5 shows that schedule, not the main one
    @Test
    void opensAnotherOfMySchedules() throws Exception {
        scheduleService.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());
        NextSemesterSchedule afternoon = saveSchedule(priya, "Afternoon labs", false);

        mockMvc.perform(get("/student/schedules/{id}", afternoon.getId()).session(sessionFor(priya)))
                .andExpect(status().isOk())
                .andExpect(view().name("student/next-semester-schedule"))
                .andExpect(content().string(containsString("Afternoon labs")))
                .andExpect(content().string(containsString("This isn't your main schedule")))
                .andExpect(content().string(containsString("No courses yet")))
                .andExpect(content().string(not(containsString("D100"))));
    }

    @Test
    void cantOpenAnotherStudentsSchedule() throws Exception {
        NextSemesterSchedule marcusSchedule = saveSchedule(marcus, "My schedule", true);

        mockMvc.perform(get("/student/schedules/{id}", marcusSchedule.getId()).session(sessionFor(priya)))
                .andExpect(redirectedUrl("/student/schedules"))
                .andExpect(flash().attribute("errors", List.of("That schedule isn't one of yours.")));
    }

    // Choosing a course on an opened schedule stays on it, and the add form sends the schedule's id
    @Test
    void choosingACourseOnAnOpenedScheduleStaysOnIt() throws Exception {
        NextSemesterSchedule afternoon = saveSchedule(priya, "Afternoon labs", false);

        String html = mockMvc.perform(get("/student/schedules/{id}", afternoon.getId()).param("code", "CMPT 225")
                .session(sessionFor(priya))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("action=\"/student/schedules/" + afternoon.getId() + "\"")
                .contains("name=\"scheduleId\" value=\"" + afternoon.getId() + "\"");
        assertThat(dropdown(html, "lecture-select")).contains("D100");
    }

    @Test
    void addingToAnOpenedScheduleGoesIntoItAndComesBack() throws Exception {
        saveSchedule(priya, "My schedule", true);
        NextSemesterSchedule afternoon = saveSchedule(priya, "Afternoon labs", false);

        mockMvc.perform(post("/student/schedule/next-semester/courses").session(sessionFor(priya))
                .param("offeringId", cmpt225.getId().toString())
                .param("primarySectionId", d100.getId().toString())
                .param("attachedSectionId", d101.getId().toString())
                .param("scheduleId", afternoon.getId().toString()))
                .andExpect(redirectedUrl("/student/schedules/" + afternoon.getId()))
                .andExpect(flash().attribute("message", "Course added."));

        assertThat(scheduleService.coursesFor(afternoon)).hasSize(1);
        assertThat(scheduleService.coursesFor(priya)).isEmpty();
    }

    @Test
    void cantAddToAnotherStudentsSchedule() throws Exception {
        NextSemesterSchedule marcusSchedule = saveSchedule(marcus, "My schedule", true);

        mockMvc.perform(post("/student/schedule/next-semester/courses").session(sessionFor(priya))
                .param("offeringId", cmpt225.getId().toString())
                .param("primarySectionId", d100.getId().toString())
                .param("attachedSectionId", d101.getId().toString())
                .param("scheduleId", marcusSchedule.getId().toString()))
                .andExpect(flash().attribute("errors", List.of("That schedule isn't one of yours.")));

        assertThat(scheduleService.coursesFor(marcusSchedule)).isEmpty();
    }

    @Test
    void removingFromAnOpenedScheduleComesBack() throws Exception {
        NextSemesterSchedule afternoon = saveSchedule(priya, "Afternoon labs", false);
        scheduleService.addCourseTo(priya, afternoon.getId(), cmpt225.getId(), d100.getId(), d101.getId());
        Long courseId = scheduleService.coursesFor(afternoon).get(0).getId();

        mockMvc.perform(post("/student/schedule/next-semester/courses/{id}/remove", courseId)
                .param("scheduleId", afternoon.getId().toString())
                .session(sessionFor(priya)))
                .andExpect(redirectedUrl("/student/schedules/" + afternoon.getId()))
                .andExpect(flash().attribute("message", "Course removed."));

        assertThat(scheduleService.coursesFor(afternoon)).isEmpty();

    }
}