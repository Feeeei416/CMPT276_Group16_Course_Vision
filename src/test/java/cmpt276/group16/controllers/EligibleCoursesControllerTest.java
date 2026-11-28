package cmpt276.group16.controllers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.transcript.ParsedCourse;
import cmpt276.group16.transcript.TranscriptService;

@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class EligibleCoursesControllerTest {

    // Replaces the real clock, so next semester is always 2027 Spring in these tests
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
    private TranscriptService transcriptService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private RequisiteRuleRepository requisiteRuleRepository;

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        SessionUser.signIn(session, user);
        return session;
    }

    private StudentProfile saveStudent(String email, String name) {
        User user = userRepository.save(new User(email, "hash", name, Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private Term nextSemester() {
        return termRepository.findByTermYearAndSeason(2027, Season.SPRING)
                .orElseGet(() -> termRepository.save(new Term(2027, Season.SPRING)));
    }

    // A course offered in 2027 Spring whose prerequisites were read by the parser
    private Course offered(String subject, String number, String title, String prereqText) {
        Course course = new Course(subject, number, title, new BigDecimal("3.00"), null, prereqText, null);
        course.setParseStatus(Course.ParseStatus.PARSED);
        course = courseRepository.save(course);
        courseOfferingRepository.save(new CourseOffering(course, nextSemester(), true));
        return course;
    }

    // Saves "needs <subject> <number> with <minGrade>" as the course's whole prerequisite tree
    private void requires(Course course, String subject, String number, String minGrade) {
        RequisiteRule rule = new RequisiteRule(course, null, RuleKind.PREREQUISITE, NodeType.COURSE);
        rule.setRequiredSubjectAndNumber(subject, number);
        rule.setMinGrade(minGrade);
        requisiteRuleRepository.save(rule);
    }

    private void record(StudentProfile student, ParsedCourse... courses) {
        transcriptService.replaceCourses(student, List.of(courses));
    }

    private static ParsedCourse took(String term, String subject, String number, String grade) {
        return new ParsedCourse(term, subject, number, "Title", new BigDecimal("3.00"), grade);
    }

    private String page(StudentProfile student) throws Exception {
        return mockMvc.perform(get("/student/eligible").session(sessionFor(student.getUser())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void needsALogin() throws Exception {
        mockMvc.perform(get("/student/eligible")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void advisorsAreSentToTheirOwnHome() throws Exception {
        User advisor = userRepository.save(new User("test.advisor@example.com", "hash", "Advisor", Role.ADVISOR));
        mockMvc.perform(get("/student/eligible").session(sessionFor(advisor)))
                .andExpect(redirectedUrl("/advisor/home"));
    }

    @Test
    void homeLinksToEligibleCourses() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        mockMvc.perform(get("/student/home").session(sessionFor(student.getUser())))
                .andExpect(content().string(containsString("href=\"/student/eligible\"")));
    }

    // #38 and #39: eligible courses and blocked ones are listed separately, and a blocked one says what's missing
    @Test
    void eligibleAndBlockedCoursesAreListedSeparately() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        record(student,
                took("2025 Fall", "CMPT", "125", "A"),
                took("2025 Fall", "MACM", "101", "D"));
        requires(offered("CMPT", "225", "Data Structures", "CMPT 125 with a minimum grade of C-."), "CMPT", "125", "C-");
        requires(offered("CMPT", "210", "Probability and Computing", "MACM 101 with a minimum grade of C-."),
                "MACM", "101", "C-");

        String page = page(student);

        assertThat(page).contains("Eligible courses for 2027 Spring");
        assertThat(page).containsSubsequence("You can take", "CMPT 225", "Not yet", "CMPT 210",
                "Needs MACM 101 with C- or better, you have D.");
    }

    // A course with no prerequisite text can always be taken
    @Test
    void courseWithNoPrerequisitesIsEligible() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        offered("CMPT", "105W", "Social Issues", null);

        assertThat(page(student)).containsSubsequence("You can take", "CMPT 105W", "Not yet");
    }

    @Test
    void passedCoursesAreNotListed() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        record(student, took("2025 Fall", "CMPT", "120", "B"));
        offered("CMPT", "120", "Intro Programming", null);

        assertThat(page(student)).doesNotContain("CMPT 120");
    }

    @Test
    void coursesInProgressAreNotListed() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        record(student, took("2026 Fall", "CMPT", "225", null));
        offered("CMPT", "225", "Data Structures", null);

        assertThat(page(student)).doesNotContain("CMPT 225");
    }

    // This term's course in progress counts for next term, but the student is told it isn't finished
    @Test
    void prerequisiteInProgressCountsButIsFlagged() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        record(student, took("2026 Fall", "CMPT", "125", null));
        requires(offered("CMPT", "225", "Data Structures", "CMPT 125 with a minimum grade of C-."), "CMPT", "125", "C-");

        assertThat(page(student)).containsSubsequence("You can take", "CMPT 225",
                "CMPT 125 is still in progress, and you need C- or better.", "Not yet");
    }

    // A course added for next semester itself can't count towards next semester's prerequisites
    @Test
    void courseAddedForNextSemesterDoesNotCount() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        record(student, took("2027 Spring", "CMPT", "125", null));
        requires(offered("CMPT", "225", "Data Structures", "CMPT 125 with a minimum grade of C-."), "CMPT", "125", "C-");

        assertThat(page(student)).containsSubsequence("Not yet", "CMPT 225", "Needs CMPT 125 with C- or better.");
    }

    // #38: prerequisites the parser couldn't read are shown as the Calendar text instead
    @Test
    void unreadablePrerequisitesAreShownAsText() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        Course course = offered("CMPT", "419", "Special Topics", "Permission of the instructor.");
        course.setParseStatus(Course.ParseStatus.FAILED);
        courseRepository.save(course);

        assertThat(page(student)).containsSubsequence("Check these yourself", "CMPT 419",
                "Permission of the instructor.");
    }

    @Test
    void coursesNotOfferedAreNotListed() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        Course course = courseRepository.save(
                new Course("CMPT", "379", "Principles of Compiler Design", new BigDecimal("3.00"), null, null, null));
        courseOfferingRepository.save(new CourseOffering(course, nextSemester(), false));

        assertThat(page(student)).doesNotContain("CMPT 379");
    }

    @Test
    void saysSoWhenNextSemesterHasNoCourseDataYet() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");

        mockMvc.perform(get("/student/eligible").session(sessionFor(student.getUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Course offerings for 2027 Spring have not been loaded yet")));
    }

    // Results come from the logged-in student's own record, never another student's
    @Test
    void studentsOnlySeeResultsForTheirOwnRecord() throws Exception {
        StudentProfile emma = saveStudent("emma@example.com", "Emma");
        StudentProfile sam = saveStudent("sam@example.com", "Sam");
        record(sam, took("2025 Fall", "CMPT", "125", "A"));
        requires(offered("CMPT", "225", "Data Structures", "CMPT 125 with a minimum grade of C-."), "CMPT", "125", "C-");

        assertThat(page(emma)).containsSubsequence("Not yet", "CMPT 225", "Needs CMPT 125 with C- or better.");
        assertThat(page(sam)).containsSubsequence("You can take", "CMPT 225", "Not yet");
    }
}