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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.AdvisorAssignment;
import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.Plan;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.PlanListService;
import cmpt276.group16.services.PlanListService.PlanSummary;
import cmpt276.group16.services.PlanService;
import cmpt276.group16.services.PlanService.TermPlan;

// #73: an advisor uses the plan pages for an assigned student, under /advisor/students/{id}.
// Uses the whole app like PlanControllerTest, so AuthInterceptor and the real database are part of every test.
// addFilters = false: Spring Session's filter would swap the test's session for a database one.
// No demo password, so DataSeeder's demo courses never clash with the courses these tests save
@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class AdvisorPlanControllerTest {

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
    private AdvisorAssignmentRepository advisorAssignmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private PlanService planService;

    @Autowired
    private PlanListService planListService;

    private User advisor;
    // Assigned to the advisor
    private StudentProfile jack;
    // Not assigned to the advisor
    private StudentProfile finn;
    private String jackPages;
    private String finnPages;

    @BeforeEach
    void setUp() {
        advisor = userRepository.save(new User("advisor@example.com", "hash", "Test Advisor", Role.ADVISOR));
        jack = saveStudent("jack@example.com", "Jack");
        finn = saveStudent("finn@example.com", "Finn");
        advisorAssignmentRepository.save(new AdvisorAssignment(advisor, jack));
        jackPages = "/advisor/students/" + jack.getUserId();
        finnPages = "/advisor/students/" + finn.getUserId();
        for (String number : List.of("225", "276")) {
            courseRepository.save(new Course("CMPT", number, "Title of CMPT " + number, new BigDecimal("3.00"),
                    null, null, null));
        }
    }

    private StudentProfile saveStudent(String email, String name) {
        User user = userRepository.save(new User(email, "hash", name, Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        SessionUser.signIn(session, user);
        return session;
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

    private Long planId(StudentProfile student, String name) {
        return planListService.plans(student).stream().map(PlanSummary::plan)
                .filter(plan -> plan.getName().equals(name)).map(Plan::getId).findFirst().orElseThrow();
    }

    // -- My plan --

    // The same page the student sees, with its forms and links pointing at the advisor's pages for that student
    @Test
    void advisorOpensTheStudentsPlan() throws Exception {
        planService.addTerm(jack, 2027, "FALL");
        planService.addCourse(jack, termId(jack, "2027 Fall"), "CMPT 276");

        mockMvc.perform(get(jackPages + "/plan").session(sessionFor(advisor)))
               .andExpect(status().isOk())
               .andExpect(view().name("student/plan"))
               .andExpect(content().string(containsString("Planning for Jack (jack@example.com).")))
               .andExpect(content().string(containsString("All their plans")))
               .andExpect(content().string(containsString("Back to Jack")))
               .andExpect(content().string(containsString("CMPT 276")))
               .andExpect(content().string(containsString("action=\"" + jackPages + "/plan/terms\"")))
               .andExpect(content().string(containsString("href=\"" + jackPages + "/plans\"")))
               .andExpect(content().string(containsString("href=\"" + jackPages + "\"")))
               .andExpect(content().string(not(containsString("\"/student/"))));
    }

    // The student's own page still points at the student's pages
    @Test
    void studentsOwnPlanKeepsItsLinks() throws Exception {
        mockMvc.perform(get("/student/plan").session(sessionFor(jack.getUser())))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("action=\"/student/plan/terms\"")))
               .andExpect(content().string(containsString("href=\"/student/plans\"")))
               .andExpect(content().string(containsString("href=\"/student/home\"")));
    }

    // Saved to the student's plan, so the student sees it on their own page
    @Test
    void advisorAddsATermTheStudentSees() throws Exception {
        String redirect = mockMvc.perform(post(jackPages + "/plan/terms").session(sessionFor(advisor))
                        .param("year", "2027").param("season", "FALL"))
               .andExpect(flash().attribute("message", "Added 2027 Fall to your plan."))
               .andReturn().getResponse().getRedirectedUrl();

        assertThat(termNames(jack)).containsExactly("2027 Fall");
        assertThat(redirect).isEqualTo(jackPages + "/plan#term-" + termId(jack, "2027 Fall"));
        mockMvc.perform(get("/student/plan").session(sessionFor(jack.getUser())))
               .andExpect(content().string(containsString("2027 Fall")));
    }

    @Test
    void advisorAddsAndRemovesACourse() throws Exception {
        planService.addTerm(jack, 2027, "FALL");
        Long fall = termId(jack, "2027 Fall");

        mockMvc.perform(post(jackPages + "/plan/terms/{id}/courses", fall).session(sessionFor(advisor))
                        .param("code", "CMPT 225"))
               .andExpect(redirectedUrl(jackPages + "/plan#term-" + fall))
               .andExpect(flash().attribute("message", "Added CMPT 225 to 2027 Fall."));
        assertThat(courseCodes(jack)).containsExactly("CMPT 225");

        mockMvc.perform(post(jackPages + "/plan/courses/{id}/remove", courseId(jack, "CMPT 225"))
                        .session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/plan#term-" + fall))
               .andExpect(flash().attribute("message", "Removed CMPT 225 from 2027 Fall."));
        assertThat(courseCodes(jack)).isEmpty();
    }

    @Test
    void advisorRemovesATerm() throws Exception {
        planService.addTerm(jack, 2027, "FALL");

        mockMvc.perform(post(jackPages + "/plan/terms/{id}/remove", termId(jack, "2027 Fall"))
                        .session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/plan"))
               .andExpect(flash().attribute("message", "Removed 2027 Fall."));
        assertThat(termNames(jack)).isEmpty();
    }

    // -- Only assigned students --

    @Test
    void unassignedStudentsPlanIsRefused() throws Exception {
        mockMvc.perform(get(finnPages + "/plan").session(sessionFor(advisor)))
               .andExpect(status().isForbidden());
        mockMvc.perform(get(finnPages + "/plans").session(sessionFor(advisor)))
               .andExpect(status().isForbidden());
    }

    // Changes are refused too, and nothing is saved
    @Test
    void unassignedStudentsPlanCantBeChanged() throws Exception {
        planService.addTerm(finn, 2027, "FALL");
        Long finnFall = termId(finn, "2027 Fall");

        mockMvc.perform(post(finnPages + "/plan/terms").session(sessionFor(advisor))
                        .param("year", "2028").param("season", "SPRING"))
               .andExpect(status().isForbidden());
        mockMvc.perform(post(finnPages + "/plan/terms/{id}/courses", finnFall).session(sessionFor(advisor))
                        .param("code", "CMPT 225"))
               .andExpect(status().isForbidden());
        mockMvc.perform(post(finnPages + "/plan/terms/{id}/remove", finnFall).session(sessionFor(advisor)))
               .andExpect(status().isForbidden());
        mockMvc.perform(post(finnPages + "/plans").session(sessionFor(advisor)).param("name", "Co-op plan"))
               .andExpect(status().isForbidden());

        assertThat(termNames(finn)).containsExactly("2027 Fall");
        assertThat(courseCodes(finn)).isEmpty();
        assertThat(planListService.plans(finn)).hasSize(1);
    }

    // The assigned student's address can't be used to change another student's term:
    // the term is looked up in the assigned student's plans only
    @Test
    void assignedStudentsPagesCantReachAnotherStudentsTerm() throws Exception {
        planService.addTerm(finn, 2027, "FALL");
        Long finnFall = termId(finn, "2027 Fall");

        mockMvc.perform(post(jackPages + "/plan/terms/{id}/courses", finnFall).session(sessionFor(advisor))
                        .param("code", "CMPT 225"))
               .andExpect(flash().attribute("errors", contains("That term isn't in your plan.")));
        mockMvc.perform(post(jackPages + "/plan/terms/{id}/remove", finnFall).session(sessionFor(advisor)))
               .andExpect(flash().attribute("errors", contains("That term isn't in your plan.")));

        assertThat(termNames(finn)).containsExactly("2027 Fall");
        assertThat(courseCodes(finn)).isEmpty();
    }

    // AuthInterceptor sends a student to their own home, even for their own advisor pages
    @Test
    void studentCantUseTheAdvisorPages() throws Exception {
        mockMvc.perform(get(jackPages + "/plan").session(sessionFor(jack.getUser())))
               .andExpect(redirectedUrl("/student/home"));
        mockMvc.perform(post(jackPages + "/plan/terms").session(sessionFor(jack.getUser()))
                        .param("year", "2027").param("season", "FALL"))
               .andExpect(redirectedUrl("/student/home"));

        assertThat(termNames(jack)).isEmpty();
    }

    @Test
    void signedOutVisitorGoesToLogin() throws Exception {
        mockMvc.perform(get(jackPages + "/plan"))
               .andExpect(redirectedUrl("/login"));
    }

    // -- One of the student's other plans --

    // A plan opened from the list keeps its id in every form, so changes go to that plan and come back to it
    @Test
    void advisorOpensOneOfTheStudentsOtherPlans() throws Exception {
        planListService.create(jack, "My plan", null);
        planListService.create(jack, "Co-op plan", null);
        Long coop = planId(jack, "Co-op plan");

        mockMvc.perform(get(jackPages + "/plans/{id}", coop).session(sessionFor(advisor)))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("This isn't their main plan")))
               .andExpect(content().string(containsString("name=\"planId\" value=\"" + coop + "\"")))
               .andExpect(content().string(not(containsString("\"/student/"))));

        String redirect = mockMvc.perform(post(jackPages + "/plan/terms").session(sessionFor(advisor))
                        .param("year", "2027").param("season", "FALL").param("planId", coop.toString()))
               .andReturn().getResponse().getRedirectedUrl();

        assertThat(redirect).startsWith(jackPages + "/plans/" + coop + "#term-");
    }

    // Another student's plan id under the assigned student's address goes back to the assigned student's list
    @Test
    void anotherStudentsPlanIdGoesBackToTheList() throws Exception {
        planListService.create(finn, "My plan", null);

        mockMvc.perform(get(jackPages + "/plans/{id}", planId(finn, "My plan")).session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/plans"))
               .andExpect(flash().attribute("errors", contains("That plan isn't one of yours.")));
    }

    // -- All my plans --

    @Test
    void advisorOpensTheStudentsPlanList() throws Exception {
        planListService.create(jack, "My plan", null);
        planListService.create(jack, "Co-op plan", null);

        mockMvc.perform(get(jackPages + "/plans").session(sessionFor(advisor)))
               .andExpect(status().isOk())
               .andExpect(view().name("student/plans"))
               .andExpect(content().string(containsString("<h1 class=\"page-title\">Plans for Jack</h1>")))
               .andExpect(content().string(containsString("Planning for Jack (jack@example.com).")))
               .andExpect(content().string(containsString(
                       "href=\"" + jackPages + "/plans/" + planId(jack, "Co-op plan") + "\"")))
               .andExpect(content().string(containsString("action=\"" + jackPages + "/plans\"")))
               .andExpect(content().string(containsString("href=\"" + jackPages + "\"")))
               .andExpect(content().string(not(containsString("\"/student/"))));
    }

    // The student's own pages say "my" and have no advisor line
    @Test
    void studentsOwnPagesKeepTheirWording() throws Exception {
        planListService.create(jack, "My plan", null);

        mockMvc.perform(get("/student/plan").session(sessionFor(jack.getUser())))
               .andExpect(content().string(containsString("All my plans")))
               .andExpect(content().string(containsString("Back to home")))
               .andExpect(content().string(not(containsString("Planning for"))));
        mockMvc.perform(get("/student/plans").session(sessionFor(jack.getUser())))
               .andExpect(content().string(containsString("<h1 class=\"page-title\">All my plans</h1>")))
               .andExpect(content().string(not(containsString("Planning for"))));
    }

    // The student's own list still points at the student's pages
    @Test
    void studentsOwnPlanListKeepsItsLinks() throws Exception {
        planListService.create(jack, "My plan", null);

        mockMvc.perform(get("/student/plans").session(sessionFor(jack.getUser())))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("action=\"/student/plans\"")))
               .andExpect(content().string(containsString("href=\"/student/plans/" + planId(jack, "My plan") + "\"")))
               .andExpect(content().string(containsString("href=\"/student/home\"")));
    }

    @Test
    void advisorMakesAPlanAndMakesItMain() throws Exception {
        planListService.create(jack, "My plan", null);

        mockMvc.perform(post(jackPages + "/plans").session(sessionFor(advisor)).param("name", "Co-op plan"))
               .andExpect(redirectedUrl(jackPages + "/plans"))
               .andExpect(flash().attribute("message", "Made Co-op plan."));
        Long coop = planId(jack, "Co-op plan");

        mockMvc.perform(post(jackPages + "/plans/{id}/main", coop).session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/plans"));

        assertThat(planService.mainPlan(jack)).map(Plan::getId).contains(coop);
    }

    @Test
    void advisorRenamesAndDeletesAPlan() throws Exception {
        planListService.create(jack, "My plan", null);
        planListService.create(jack, "Co-op plan", null);
        Long coop = planId(jack, "Co-op plan");

        mockMvc.perform(post(jackPages + "/plans/{id}/rename", coop).session(sessionFor(advisor))
                        .param("name", "Lighter plan"))
               .andExpect(redirectedUrl(jackPages + "/plans"));
        mockMvc.perform(post(jackPages + "/plans/{id}/delete", coop).session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/plans"));

        assertThat(planListService.plans(jack)).extracting(summary -> summary.plan().getName())
                .containsExactly("My plan");
    }
}
