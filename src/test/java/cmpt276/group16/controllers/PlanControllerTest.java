package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.PlanService;

// Uses the whole app, so AuthInterceptor and the real database are part of every test.
// addFilters = false: Spring Session's filter would swap the test's session for a database one
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class PlanControllerTest {

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
    private PlanService planService;

    private StudentProfile priya;
    private StudentProfile marcus;

    @BeforeEach
    void setUp() {
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.save(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginController.SESSION_USER, user);
        return session;
    }

    private MockHttpSession sessionFor(StudentProfile student) {
        return sessionFor(student.getUser());
    }

    private Long termId(StudentProfile student, String termName) {
        return planService.coursesByTerm(student).keySet().stream()
                .filter(term -> term.getName().equals(termName))
                .map(PlanTerm::getId)
                .findFirst().orElseThrow();
    }

    private Long courseId(StudentProfile student, String code) {
        return planService.coursesByTerm(student).values().stream()
                .flatMap(List::stream)
                .filter(course -> course.getCode().equals(code))
                .map(PlannedCourse::getId)
                .findFirst().orElseThrow();
    }

    private List<String> termNames(StudentProfile student) {
        return planService.coursesByTerm(student).keySet().stream().map(PlanTerm::getName).toList();
    }

    private List<String> courseCodes(StudentProfile student) {
        return planService.coursesByTerm(student).values().stream()
                .flatMap(List::stream)
                .map(PlannedCourse::getCode)
                .toList();
    }

    // -- Who can open the page --

    @Test
    void signedOutVisitorGoesToLogin() throws Exception {
        mockMvc.perform(get("/student/plan"))
               .andExpect(redirectedUrl("/login"));
    }

    // AuthInterceptor sends other roles to their own home page, because the address starts with /student/
    @ParameterizedTest
    @EnumSource(value = Role.class, names = "STUDENT", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesAreSentHome(Role role) throws Exception {
        User user = userRepository.save(new User("staff@example.com", "hash", "Staff", role));

        mockMvc.perform(get("/student/plan").session(sessionFor(user)))
               .andExpect(redirectedUrl(LoginController.homePath(role)));
    }

    // -- Showing the plan --

    @Test
    void emptyPlanExplainsHowToStart() throws Exception {
        mockMvc.perform(get("/student/plan").session(sessionFor(priya)))
               .andExpect(status().isOk())
               .andExpect(view().name("student/plan"))
               .andExpect(content().string(containsString("No terms yet")))
               .andExpect(content().string(containsString("2027 Spring")))
               .andExpect(content().string(containsString("<option value=\"2027\">2027</option>")))
               .andExpect(content().string(containsString("<option value=\"2033\">2033</option>")));
    }

    @Test
    void showsTermsAndCourses() throws Exception {
        planService.addTerm(priya, 2027, "Fall");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");

        mockMvc.perform(get("/student/plan").session(sessionFor(priya)))
               .andExpect(content().string(containsString("2027 Fall")))
               .andExpect(content().string(containsString("CMPT 276")))
               .andExpect(content().string(not(containsString("No terms yet"))));
    }

    // Remove term asks in a dialog on the page, naming the courses that go with it, not in a browser pop-up
    @Test
    void removeTermAsksFirstInADialog() throws Exception {
        planService.addTerm(priya, 2027, "Fall");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 295");

        mockMvc.perform(get("/student/plan").session(sessionFor(priya)))
               .andExpect(content().string(containsString("<dialog")))
               .andExpect(content().string(containsString("Remove 2027 Fall?")))
               .andExpect(content().string(containsString("This also removes CMPT 276, CMPT 295 from your plan.")))
               .andExpect(content().string(not(containsString("confirm("))));
    }

    // Each student only sees their own plan
    @Test
    void doesntShowAnotherStudentsPlan() throws Exception {
        planService.addTerm(priya, 2027, "Fall");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");

        mockMvc.perform(get("/student/plan").session(sessionFor(marcus)))
               .andExpect(content().string(containsString("No terms yet")))
               .andExpect(content().string(not(containsString("CMPT 276"))));
    }

    // Errors from a change arrive as a flash attribute after the redirect
    @Test
    void showsErrorsFromTheLastChange() throws Exception {
        mockMvc.perform(get("/student/plan").session(sessionFor(priya))
                        .flashAttr("errors", List.of("Choose a year.")))
               .andExpect(content().string(containsString("Choose a year.")));
    }

    // The plan is saved in the database, not the session, so a new login still sees it
    @Test
    void planIsStillThereAfterLoggingInAgain() throws Exception {
        mockMvc.perform(post("/student/plan/terms").session(sessionFor(priya))
                        .param("year", "2027").param("season", "Fall"));

        mockMvc.perform(get("/student/plan").session(sessionFor(priya)))
               .andExpect(content().string(containsString("2027 Fall")));
    }

    // -- Terms --

    @Test
    void addsATerm() throws Exception {
        mockMvc.perform(post("/student/plan/terms").session(sessionFor(priya))
                        .param("year", "2027").param("season", "Fall"))
               .andExpect(redirectedUrl("/student/plan"))
               .andExpect(flash().attribute("message", "Your plan was saved."));

        assertThat(termNames(priya)).containsExactly("2027 Fall");
    }

    @Test
    void termTheRulesDontAllowShowsAnError() throws Exception {
        mockMvc.perform(post("/student/plan/terms").session(sessionFor(priya))
                        .param("year", "2027").param("season", "Spring"))
               .andExpect(redirectedUrl("/student/plan"))
               .andExpect(flash().attribute("errors", contains("Choose a term after next semester (2027 Spring).")));

        assertThat(termNames(priya)).isEmpty();
    }

    // The year dropdown only sends numbers, but the request can be edited
    @Test
    void yearThatIsntANumberShowsAnError() throws Exception {
        mockMvc.perform(post("/student/plan/terms").session(sessionFor(priya))
                        .param("year", "next year").param("season", "Fall"))
               .andExpect(flash().attribute("errors", contains("Choose a year.")));

        assertThat(termNames(priya)).isEmpty();
    }

    @Test
    void removesATerm() throws Exception {
        planService.addTerm(priya, 2027, "Fall");

        mockMvc.perform(post("/student/plan/terms/{id}/remove", termId(priya, "2027 Fall")).session(sessionFor(priya)))
               .andExpect(redirectedUrl("/student/plan"))
               .andExpect(flash().attribute("message", "Your plan was saved."));

        assertThat(termNames(priya)).isEmpty();
    }

    // -- Courses --

    @Test
    void addsACourse() throws Exception {
        planService.addTerm(priya, 2027, "Fall");

        mockMvc.perform(post("/student/plan/terms/{id}/courses", termId(priya, "2027 Fall")).session(sessionFor(priya))
                        .param("code", "cmpt 225"))
               .andExpect(redirectedUrl("/student/plan"))
               .andExpect(flash().attribute("message", "Your plan was saved."));

        assertThat(courseCodes(priya)).containsExactly("CMPT 225");
    }

    @Test
    void textThatIsntACourseCodeShowsAnError() throws Exception {
        planService.addTerm(priya, 2027, "Fall");

        mockMvc.perform(post("/student/plan/terms/{id}/courses", termId(priya, "2027 Fall")).session(sessionFor(priya))
                        .param("code", "data structures"))
               .andExpect(flash().attribute("errors", contains("Enter a course code like CMPT 225.")));

        assertThat(courseCodes(priya)).isEmpty();
    }

    @Test
    void removesACourse() throws Exception {
        planService.addTerm(priya, 2027, "Fall");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 225");

        mockMvc.perform(post("/student/plan/courses/{id}/remove", courseId(priya, "CMPT 225")).session(sessionFor(priya)))
               .andExpect(redirectedUrl("/student/plan"));

        assertThat(courseCodes(priya)).isEmpty();
    }

    // -- Another student's ids --

    // Marcus changes the ids in the request to Priya's; nothing of hers changes
    @Test
    void anotherStudentsIdsAreRejected() throws Exception {
        planService.addTerm(priya, 2027, "Fall");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 225");
        Long priyasTerm = termId(priya, "2027 Fall");
        Long priyasCourse = courseId(priya, "CMPT 225");

        mockMvc.perform(post("/student/plan/terms/{id}/courses", priyasTerm).session(sessionFor(marcus))
                        .param("code", "CMPT 276"))
               .andExpect(flash().attribute("errors", contains("That term isn't in your plan.")));
        mockMvc.perform(post("/student/plan/courses/{id}/remove", priyasCourse).session(sessionFor(marcus)))
               .andExpect(flash().attribute("errors", contains("That course isn't in your plan.")));
        mockMvc.perform(post("/student/plan/terms/{id}/remove", priyasTerm).session(sessionFor(marcus)))
               .andExpect(flash().attribute("errors", contains("That term isn't in your plan.")));

        assertThat(termNames(priya)).containsExactly("2027 Fall");
        assertThat(courseCodes(priya)).containsExactly("CMPT 225");
    }
}
