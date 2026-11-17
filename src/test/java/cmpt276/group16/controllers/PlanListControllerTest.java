package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Plan;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.PlanListService;
import cmpt276.group16.services.PlanListService.PlanSummary;

// Uses the whole app, so AuthInterceptor and the real database are part of every test.
// addFilters = false: Spring Session's filter would swap the test's session for a database one
@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class PlanListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PlanListService planListService;

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

    private Long planId(StudentProfile student, String name) {
        return planListService.plans(student).stream().map(PlanSummary::plan)
                .filter(plan -> plan.getName().equals(name)).map(Plan::getId).findFirst().orElseThrow();
    }

    private List<String> planNames(StudentProfile student) {
        return planListService.plans(student).stream().map(summary -> summary.plan().getName()).toList();
    }

    // -- Who can open the page --

    @Test
    void signedOutVisitorGoesToLogin() throws Exception {
        mockMvc.perform(get("/student/plans"))
               .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "STUDENT", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesAreSentHome(Role role) throws Exception {
        User user = userRepository.save(new User("staff@example.com", "hash", "Staff", role));

        mockMvc.perform(get("/student/plans").session(sessionFor(user)))
               .andExpect(redirectedUrl(LoginController.homePath(role)));
    }

    // -- Showing the plans --

    @Test
    void noPlansExplainsHowToStart() throws Exception {
        mockMvc.perform(get("/student/plans").session(sessionFor(priya)))
               .andExpect(status().isOk())
               .andExpect(view().name("student/plans"))
               .andExpect(content().string(containsString("No plans yet")));
    }

    @Test
    void showsEachPlanWithTheMainOneMarked() throws Exception {
        planListService.create(priya, "My plan", null);
        planListService.create(priya, "Co-op plan", null);

        mockMvc.perform(get("/student/plans").session(sessionFor(priya)))
               .andExpect(content().string(containsString("My plan")))
               .andExpect(content().string(containsString("Co-op plan")))
               .andExpect(content().string(containsString("Main plan")))
               .andExpect(content().string(containsString("0 terms, 0 courses")))
               .andExpect(content().string(containsString("Copy of Co-op plan")))
               .andExpect(content().string(not(containsString("No plans yet"))));
    }

    // Delete asks in a dialog on the page, and the main plan has no Delete at all
    @Test
    void onlyOtherPlansCanBeDeletedAndDeleteAsksFirst() throws Exception {
        planListService.create(priya, "My plan", null);
        planListService.create(priya, "Co-op plan", null);
        String page = mockMvc.perform(get("/student/plans").session(sessionFor(priya)))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Delete Co-op plan?")
                .doesNotContain("Delete My plan?")
                .doesNotContain("confirm(");
        assertThat(page).contains("/student/plans/" + planId(priya, "Co-op plan") + "/delete")
                .doesNotContain("/student/plans/" + planId(priya, "My plan") + "/delete");
    }

    @Test
    void eachStudentOnlySeesTheirOwnPlans() throws Exception {
        planListService.create(marcus, "Marcus's plan", null);

        mockMvc.perform(get("/student/plans").session(sessionFor(priya)))
               .andExpect(content().string(not(containsString("Marcus's plan"))));
    }

    // -- Changes --

    @Test
    void makingAPlanSaysSoAndGoesBackToThePage() throws Exception {
        mockMvc.perform(post("/student/plans").param("name", "Co-op plan").param("copyFrom", "")
                       .session(sessionFor(priya)))
               .andExpect(redirectedUrl("/student/plans"))
               .andExpect(flash().attribute("message", "Made Co-op plan."));

        assertThat(planNames(priya)).containsExactly("Co-op plan");
    }

    @Test
    void copyingAPlan() throws Exception {
        planListService.create(priya, "My plan", null);

        mockMvc.perform(post("/student/plans").param("name", "Co-op plan")
                       .param("copyFrom", planId(priya, "My plan").toString())
                       .session(sessionFor(priya)))
               .andExpect(flash().attribute("message", "Made Co-op plan as a copy of My plan."));
    }

    // The name and choice go back into the form, so fixing the name doesn't mean starting again
    @Test
    void failedNewPlanKeepsWhatWasTyped() throws Exception {
        planListService.create(priya, "My plan", null);
        String myPlanId = planId(priya, "My plan").toString();

        mockMvc.perform(post("/student/plans").param("name", "my plan").param("copyFrom", myPlanId)
                       .session(sessionFor(priya)))
               .andExpect(flash().attribute("errors", List.of("You already have a plan called my plan.")))
               .andExpect(flash().attribute("typedName", "my plan"))
               .andExpect(flash().attribute("chosenCopyFrom", myPlanId));
    }

    @Test
    void typedNameAndChoiceComeBackIntoTheForm() throws Exception {
        planListService.create(priya, "My plan", null);
        String myPlanId = planId(priya, "My plan").toString();

        String page = mockMvc.perform(get("/student/plans").session(sessionFor(priya))
                        .flashAttr("typedName", "my plan").flashAttr("chosenCopyFrom", myPlanId)
                        .flashAttr("errors", List.of("You already have a plan called my plan.")))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("value=\"my plan\"")
                .containsPattern("value=\"" + myPlanId + "\"\\s+selected=\"selected\">Copy of My plan")
                .contains("You already have a plan called my plan.");
    }

    // Text instead of a plan id is treated like an id that isn't the student's
    @Test
    void copyFromThatIsntANumberIsRejected() throws Exception {
        mockMvc.perform(post("/student/plans").param("name", "Co-op plan").param("copyFrom", "abc")
                       .session(sessionFor(priya)))
               .andExpect(flash().attribute("errors", List.of("The plan to copy isn't one of your plans.")));

        assertThat(planNames(priya)).isEmpty();
    }

    @Test
    void renamingAPlan() throws Exception {
        planListService.create(priya, "My plan", null);

        mockMvc.perform(post("/student/plans/{id}/rename", planId(priya, "My plan")).param("name", "Four-year plan")
                       .session(sessionFor(priya)))
               .andExpect(redirectedUrl("/student/plans"))
               .andExpect(flash().attribute("message", "Renamed My plan to Four-year plan."));

        assertThat(planNames(priya)).containsExactly("Four-year plan");
    }

    @Test
    void deletingAPlan() throws Exception {
        planListService.create(priya, "My plan", null);
        planListService.create(priya, "Co-op plan", null);

        mockMvc.perform(post("/student/plans/{id}/delete", planId(priya, "Co-op plan")).session(sessionFor(priya)))
               .andExpect(flash().attribute("message", "Deleted Co-op plan."));

        assertThat(planNames(priya)).containsExactly("My plan");
    }

    @Test
    void makingAnotherPlanMain() throws Exception {
        planListService.create(priya, "My plan", null);
        planListService.create(priya, "Co-op plan", null);

        mockMvc.perform(post("/student/plans/{id}/main", planId(priya, "Co-op plan")).session(sessionFor(priya)))
               .andExpect(flash().attribute("message", "Co-op plan is now your main plan."));

        assertThat(planNames(priya)).containsExactly("Co-op plan", "My plan");
    }

    // Changing the id in the form finds nothing, and Marcus's plans stay as they were
    @Test
    void cantChangeAnotherStudentsPlans() throws Exception {
        planListService.create(marcus, "Marcus's plan", null);
        planListService.create(marcus, "Marcus's co-op", null);
        Long id = planId(marcus, "Marcus's co-op");
        MockHttpSession session = sessionFor(priya);

        mockMvc.perform(post("/student/plans/{id}/rename", id).param("name", "Mine").session(session))
               .andExpect(flash().attribute("errors", List.of("That plan isn't one of yours.")));
        mockMvc.perform(post("/student/plans/{id}/main", id).session(session))
               .andExpect(flash().attribute("errors", List.of("That plan isn't one of yours.")));
        mockMvc.perform(post("/student/plans/{id}/delete", id).session(session))
               .andExpect(flash().attribute("errors", List.of("That plan isn't one of yours.")));

        assertThat(planNames(marcus)).containsExactly("Marcus's plan", "Marcus's co-op");
    }

    @Test
    void signedOutChangesGoToLogin() throws Exception {
        mockMvc.perform(post("/student/plans").param("name", "Co-op plan"))
               .andExpect(redirectedUrl("/login"));
    }

    // -- Getting here --

    @Test
    void homeAndMyPlanLinkToMyPlans() throws Exception {
        mockMvc.perform(get("/student/home").session(sessionFor(priya)))
               .andExpect(content().string(containsString("href=\"/student/plans\"")));
        mockMvc.perform(get("/student/plan").session(sessionFor(priya)))
               .andExpect(content().string(containsString("href=\"/student/plans\"")));
    }
}
