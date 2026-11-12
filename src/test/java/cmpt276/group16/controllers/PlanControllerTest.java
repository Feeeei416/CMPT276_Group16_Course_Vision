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

import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.PlanService;
import cmpt276.group16.services.PlanService.TermPlan;

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
    private CourseRepository courseRepository;

    @Autowired
    private PlanService planService;

    private StudentProfile priya;
    private StudentProfile marcus;

    @BeforeEach
    void setUp() {
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");
        // The courses the tests plan; anything else isn't in the course list
        for (String number : List.of("225", "276", "295")) {
            courseRepository.save(new Course("CMPT", number, "Title of CMPT " + number, new BigDecimal("3.00"),
                    null, null, null));
        }
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
        return planService.termPlans(student).stream()
                .map(TermPlan::term)
                .filter(term -> term.getName().equals(termName))
                .map(PlanTerm::getId)
                .findFirst().orElseThrow();
    }

    private Long courseId(StudentProfile student, String code) {
        return planService.termPlans(student).stream()
                .flatMap(termPlan -> termPlan.courses().stream())
                .filter(course -> course.getCode().equals(code))
                .map(PlannedCourse::getId)
                .findFirst().orElseThrow();
    }

    private List<String> termNames(StudentProfile student) {
        return planService.termPlans(student).stream().map(termPlan -> termPlan.term().getName()).toList();
    }

    private List<String> courseCodes(StudentProfile student) {
        return planService.termPlans(student).stream()
                .flatMap(termPlan -> termPlan.courses().stream())
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
               .andExpect(content().string(containsString("<option value=\"2033\">2033</option>")))
               .andExpect(content().string(containsString("<option value=\"FALL\">Fall</option>")));
    }

    // Each term shows its courses with their titles, and the term's total units
    @Test
    void showsTermsCoursesAndUnits() throws Exception {
        planService.addTerm(priya, 2027, "FALL");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 295");

        mockMvc.perform(get("/student/plan").session(sessionFor(priya)))
               .andExpect(content().string(containsString("2027 Fall")))
               .andExpect(content().string(containsString("CMPT 276")))
               .andExpect(content().string(containsString("Title of CMPT 276")))
               .andExpect(content().string(containsString("6 units")))
               .andExpect(content().string(not(containsString("No terms yet"))));
    }

    // Remove term asks in a dialog on the page, naming the courses that go with it, not in a browser pop-up
    @Test
    void removeTermAsksFirstInADialog() throws Exception {
        planService.addTerm(priya, 2027, "FALL");
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
        planService.addTerm(priya, 2027, "FALL");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");

        mockMvc.perform(get("/student/plan").session(sessionFor(marcus)))
               .andExpect(content().string(containsString("No terms yet")))
               .andExpect(content().string(not(containsString("CMPT 276"))));
    }

    // A change inside a term shows its message in that term's box, not at the top of the page
    @Test
    void messageShowsInsideTheTermThatChanged() throws Exception {
        planService.addTerm(priya, 2027, "SUMMER");
        planService.addTerm(priya, 2027, "FALL");
        Long fall = termId(priya, "2027 Fall");

        String page = mockMvc.perform(get("/student/plan").session(sessionFor(priya))
                        .flashAttr("message", "Added CMPT 225 to 2027 Fall.")
                        .flashAttr("changedTermId", fall))
               .andReturn().getResponse().getContentAsString();

        assertThat(page).containsOnlyOnce("Added CMPT 225 to 2027 Fall.");
        assertThat(page.indexOf("Added CMPT 225 to 2027 Fall.")).isGreaterThan(page.indexOf("id=\"term-" + fall + "\""));
    }

    // Removing a whole term leaves no box to show the message in, so it goes at the top
    @Test
    void messageWithoutATermShowsAtTheTop() throws Exception {
        planService.addTerm(priya, 2027, "SUMMER");

        String page = mockMvc.perform(get("/student/plan").session(sessionFor(priya))
                        .flashAttr("message", "Removed 2027 Fall."))
               .andReturn().getResponse().getContentAsString();

        assertThat(page).containsOnlyOnce("Removed 2027 Fall.");
        assertThat(page.indexOf("Removed 2027 Fall.")).isLessThan(page.indexOf("class=\"plan-term\""));
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
                        .param("year", "2027").param("season", "FALL"));

        mockMvc.perform(get("/student/plan").session(sessionFor(priya)))
               .andExpect(content().string(containsString("2027 Fall")));
    }

    // -- Terms --

    // Goes back to the new term, with the message shown in it
    @Test
    void addsATerm() throws Exception {
        MvcResult result = mockMvc.perform(post("/student/plan/terms").session(sessionFor(priya))
                        .param("year", "2027").param("season", "FALL"))
               .andExpect(flash().attribute("message", "Added 2027 Fall to your plan."))
               .andReturn();

        assertThat(termNames(priya)).containsExactly("2027 Fall");
        Long fall = termId(priya, "2027 Fall");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/student/plan#term-" + fall);
        assertThat(result.getFlashMap().get("changedTermId")).isEqualTo(fall);
    }

    @Test
    void termTheRulesDontAllowShowsAnError() throws Exception {
        mockMvc.perform(post("/student/plan/terms").session(sessionFor(priya))
                        .param("year", "2027").param("season", "SPRING"))
               .andExpect(redirectedUrl("/student/plan"))
               .andExpect(flash().attribute("errors", contains("Choose a term after next semester (2027 Spring).")));

        assertThat(termNames(priya)).isEmpty();
    }

    // The year dropdown only sends numbers, but the request can be edited
    @Test
    void yearThatIsntANumberShowsAnError() throws Exception {
        mockMvc.perform(post("/student/plan/terms").session(sessionFor(priya))
                        .param("year", "next year").param("season", "FALL"))
               .andExpect(flash().attribute("errors", contains("Choose a year.")));

        assertThat(termNames(priya)).isEmpty();
    }

    // The message names the courses that were removed with the term
    @Test
    void removesATerm() throws Exception {
        planService.addTerm(priya, 2027, "FALL");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 225");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");

        mockMvc.perform(post("/student/plan/terms/{id}/remove", termId(priya, "2027 Fall")).session(sessionFor(priya)))
               .andExpect(redirectedUrl("/student/plan"))
               .andExpect(flash().attribute("message", "Removed 2027 Fall and CMPT 225, CMPT 276."));

        assertThat(termNames(priya)).isEmpty();
    }

    // -- Courses --

    @Test
    void addsACourse() throws Exception {
        planService.addTerm(priya, 2027, "FALL");
        Long fall = termId(priya, "2027 Fall");

        mockMvc.perform(post("/student/plan/terms/{id}/courses", fall).session(sessionFor(priya))
                        .param("code", "cmpt 225"))
               .andExpect(redirectedUrl("/student/plan#term-" + fall))
               .andExpect(flash().attribute("changedTermId", fall))
               .andExpect(flash().attribute("message", "Added CMPT 225 to 2027 Fall."));

        assertThat(courseCodes(priya)).containsExactly("CMPT 225");
    }

    // The error goes back to the term the student was typing in
    @Test
    void textThatIsntACourseCodeShowsAnError() throws Exception {
        planService.addTerm(priya, 2027, "FALL");
        Long fall = termId(priya, "2027 Fall");

        mockMvc.perform(post("/student/plan/terms/{id}/courses", fall).session(sessionFor(priya))
                        .param("code", "data structures"))
               .andExpect(redirectedUrl("/student/plan#term-" + fall))
               .andExpect(flash().attribute("errors", contains("Enter a course code like CMPT 225.")));

        assertThat(courseCodes(priya)).isEmpty();
    }

    @Test
    void removesACourse() throws Exception {
        planService.addTerm(priya, 2027, "FALL");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 225");

        mockMvc.perform(post("/student/plan/courses/{id}/remove", courseId(priya, "CMPT 225")).session(sessionFor(priya)))
               .andExpect(redirectedUrl("/student/plan#term-" + termId(priya, "2027 Fall")))
               .andExpect(flash().attribute("message", "Removed CMPT 225 from 2027 Fall."));

        assertThat(courseCodes(priya)).isEmpty();
    }

    // -- Another student's ids --

    // Marcus changes the ids in the request to Priya's; nothing of hers changes
    @Test
    void anotherStudentsIdsAreRejected() throws Exception {
        planService.addTerm(priya, 2027, "FALL");
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
