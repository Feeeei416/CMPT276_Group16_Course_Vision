package cmpt276.group16.controllers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.CourseOfferingRepository;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.NodeType;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.RequisiteRuleRepository;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.RuleKind;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.Section;
import cmpt276.group16.models.SectionRepository;
import cmpt276.group16.models.SectionType;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.NextSemesterScheduleService;
import cmpt276.group16.transcript.ParsedCourse;
import cmpt276.group16.transcript.TranscriptService;

// #74: My schedule flags courses missing a prerequisite, the same way My plan does.
// Uses the whole app with the real database. Next semester is 2027 Spring here, and CMPT 276 needs CMPT 225
// with C- or better. Filters are off so the test's MockHttpSession is used, and @Transactional undoes each test
@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class NextSemesterSchedulePrerequisitesTest {

    // Next semester is always 2027 Spring in these tests
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
    private RequisiteRuleRepository requisiteRuleRepository;

    @Autowired
    private NextSemesterScheduleService scheduleService;

    @Autowired
    private TranscriptService transcriptService;

    private StudentProfile priya;
    private Term spring2027;
    private Course cmpt225;
    private Course cmpt276;
    private RequisiteRule needs225;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("schedule.prereq@example.com", "hash", "Priya", Role.STUDENT));
        priya = studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
        spring2027 = termRepository.save(new Term(2027, Season.SPRING));
        cmpt225 = courseRepository.save(
                new Course("CMPT", "225", "Data Structures", new BigDecimal("3"), null, null, null));
        cmpt276 = courseRepository.save(
                new Course("CMPT", "276", "Software Engineering", new BigDecimal("3"), null, null, null));
        needs225 = new RequisiteRule(cmpt276, null, RuleKind.PREREQUISITE, NodeType.COURSE);
        needs225.setRequiredSubjectAndNumber("CMPT", "225");
        needs225.setMinGrade("C-");
        needs225 = requisiteRuleRepository.save(needs225);
    }

    // Adds the course to Priya's schedule for 2027 Spring, in a lecture section D100
    private void schedule(Course course) {
        CourseOffering offering = courseOfferingRepository.save(new CourseOffering(course, spring2027, true));
        Section d100 = sectionRepository.save(new Section(offering, "D100", SectionType.LEC, true, "1"));
        assertThat(scheduleService.addCourse(priya, offering.getId(), d100.getId(), null)).isEmpty();
    }

    // grade null means the course is in progress
    private void record(String term, String number, String grade) {
        transcriptService.replaceCourses(priya,
                List.of(new ParsedCourse(term, "CMPT", number, "Title", new BigDecimal("3.00"), grade)));
    }

    private String page() throws Exception {
        MockHttpSession session = new MockHttpSession();
        SessionUser.signIn(session, priya.getUser());
        return mockMvc.perform(get("/student/schedule/next-semester").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void courseMissingAPrerequisiteIsFlaggedInRed() throws Exception {
        schedule(cmpt276);

        assertThat(page()).contains("<span class=\"plan-course-flag\">Needs CMPT 225 with C- or better.</span>");
    }

    @Test
    void metPrerequisiteShowsNothing() throws Exception {
        record("2025 Fall", "225", "B");
        schedule(cmpt276);

        assertThat(page()).doesNotContain("Needs CMPT 225").doesNotContain("plan-course-note");
    }

    @Test
    void failedPrerequisiteSaysWhatTheStudentHas() throws Exception {
        record("2025 Fall", "225", "D");
        schedule(cmpt276);

        assertThat(page()).contains("Needs CMPT 225 with C- or better, you have D.");
    }

    @Test
    void prerequisiteInProgressThisTermCountsWithANoteToConfirm() throws Exception {
        record("2026 Fall", "225", null);
        schedule(cmpt276);

        assertThat(page()).doesNotContain("Needs CMPT 225")
                .contains("CMPT 225 is still in progress, and you need C- or better.");
    }

    @Test
    void concurrentPrerequisiteInTheSameScheduleCounts() throws Exception {
        needs225.setConcurrentOk(true);
        schedule(cmpt225);
        schedule(cmpt276);

        assertThat(page()).doesNotContain("Needs CMPT 225")
                .contains("CMPT 225 is in your schedule too, and you need C- or better.");
    }

    @Test
    void prerequisiteInTheSameScheduleDoesntCountUnlessConcurrent() throws Exception {
        schedule(cmpt225);
        schedule(cmpt276);

        assertThat(page()).contains("Needs CMPT 225 with C- or better.");
    }

    // A course taken by hand for next semester or later isn't done by the time next semester starts
    @Test
    void courseInTheRecordForNextSemesterDoesntCount() throws Exception {
        record("2027 Spring", "225", "A");
        schedule(cmpt276);

        assertThat(page()).contains("Needs CMPT 225 with C- or better.");
    }
}
