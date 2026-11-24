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

import cmpt276.group16.models.NextSemesterSchedule;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.ScheduleListService;
import cmpt276.group16.services.ScheduleListService.ScheduleSummary;

// Uses the whole app, so AuthInterceptor and the real database are part of every test.
// addFilters = false: Spring Session's filter would swap the test's session for a database one.
// No demo password, so DataSeeder's demo courses never clash with the term these tests save
@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ScheduleListControllerTest {

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
    private TermRepository termRepository;

    @Autowired
    private ScheduleListService scheduleListService;

    private Term spring2027;
    private StudentProfile jack;
    private StudentProfile finn;

    @BeforeEach
    void setUp() {
        spring2027 = termRepository.save(new Term(2027, Season.SPRING));
        jack = saveStudent("jack@example.com");
        finn = saveStudent("finn@example.com");
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

    private Long scheduleId(StudentProfile student, String name) {
        return scheduleListService.schedules(student).stream().map(ScheduleSummary::schedule)
                .filter(schedule -> schedule.getName().equals(name)).map(NextSemesterSchedule::getId)
                .findFirst().orElseThrow();
    }

    private List<String> scheduleNames(StudentProfile student) {
        return scheduleListService.schedules(student).stream().map(summary -> summary.schedule().getName()).toList();
    }

    // -- Who can open the page --

    @Test
    void signedOutVisitorGoesToLogin() throws Exception {
        mockMvc.perform(get("/student/schedules"))
               .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "STUDENT", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesAreSentHome(Role role) throws Exception {
        User user = userRepository.save(new User("staff@example.com", "hash", "Staff", role));

        mockMvc.perform(get("/student/schedules").session(sessionFor(user)))
               .andExpect(redirectedUrl(LoginController.homePath(role)));
    }

    // -- Showing the schedules --

    @Test
    void noSchedulesExplainsHowToStart() throws Exception {
        mockMvc.perform(get("/student/schedules").session(sessionFor(jack)))
               .andExpect(status().isOk())
               .andExpect(view().name("student/schedules"))
               .andExpect(content().string(containsString("No schedules yet")))
               .andExpect(content().string(containsString("(2027 Spring)")));
    }

    @Test
    void showsEachScheduleWithTheMainOneMarked() throws Exception {
        scheduleListService.create(jack, "My schedule", null);
        scheduleListService.create(jack, "Morning labs", null);

        mockMvc.perform(get("/student/schedules").session(sessionFor(jack)))
               .andExpect(content().string(containsString("My schedule")))
               .andExpect(content().string(containsString("Morning labs")))
               .andExpect(content().string(containsString("Main schedule")))
               .andExpect(content().string(containsString("0 courses")))
               .andExpect(content().string(containsString("Copy of Morning labs")))
               .andExpect(content().string(not(containsString("No schedules yet"))));
    }

    // Delete asks in a dialog on the page, and the main schedule has no Delete at all
    @Test
    void onlyOtherSchedulesCanBeDeletedAndDeleteAsksFirst() throws Exception {
        scheduleListService.create(jack, "My schedule", null);
        scheduleListService.create(jack, "Morning labs", null);
        String page = mockMvc.perform(get("/student/schedules").session(sessionFor(jack)))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Delete Morning labs?")
                .contains("It has no courses in it.")
                .doesNotContain("Delete My schedule?")
                .doesNotContain("confirm(");
        assertThat(page).contains("/student/schedules/" + scheduleId(jack, "Morning labs") + "/delete")
                .doesNotContain("/student/schedules/" + scheduleId(jack, "My schedule") + "/delete");
    }

    @Test
    void eachStudentOnlySeesTheirOwnSchedules() throws Exception {
        scheduleListService.create(finn, "Finn's schedule", null);

        mockMvc.perform(get("/student/schedules").session(sessionFor(jack)))
               .andExpect(content().string(not(containsString("Finn&#39;s schedule"))))
               .andExpect(content().string(not(containsString("Finn's schedule"))));
    }

    // Until next semester's courses are imported there's nothing to schedule, so there's no form either
    @Test
    void beforeNextSemesterIsLoadedThePageSaysSo() throws Exception {
        termRepository.delete(spring2027);

        mockMvc.perform(get("/student/schedules").session(sessionFor(jack)))
               .andExpect(content().string(containsString("Next semester's courses aren't available yet")))
               .andExpect(content().string(not(containsString("Make schedule"))));
    }

    // -- Changes --

    @Test
    void makingAScheduleSaysSoAndGoesBackToThePage() throws Exception {
        mockMvc.perform(post("/student/schedules").param("name", "Morning labs").param("copyFrom", "")
                       .session(sessionFor(jack)))
               .andExpect(redirectedUrl("/student/schedules"))
               .andExpect(flash().attribute("message", "Made Morning labs."));

        assertThat(scheduleNames(jack)).containsExactly("Morning labs");
    }

    @Test
    void copyingASchedule() throws Exception {
        scheduleListService.create(jack, "My schedule", null);

        mockMvc.perform(post("/student/schedules").param("name", "Morning labs")
                       .param("copyFrom", scheduleId(jack, "My schedule").toString())
                       .session(sessionFor(jack)))
               .andExpect(flash().attribute("message", "Made Morning labs as a copy of My schedule."));
    }

    // The name and choice go back into the form, so fixing the name doesn't mean starting again
    @Test
    void failedNewScheduleKeepsWhatWasTyped() throws Exception {
        scheduleListService.create(jack, "My schedule", null);
        String myScheduleId = scheduleId(jack, "My schedule").toString();

        mockMvc.perform(post("/student/schedules").param("name", "my schedule").param("copyFrom", myScheduleId)
                       .session(sessionFor(jack)))
               .andExpect(flash().attribute("errors", List.of("You already have a schedule called my schedule.")))
               .andExpect(flash().attribute("typedName", "my schedule"))
               .andExpect(flash().attribute("chosenCopyFrom", myScheduleId));
    }

    @Test
    void typedNameAndChoiceComeBackIntoTheForm() throws Exception {
        scheduleListService.create(jack, "My schedule", null);
        String myScheduleId = scheduleId(jack, "My schedule").toString();

        String page = mockMvc.perform(get("/student/schedules").session(sessionFor(jack))
                        .flashAttr("typedName", "my schedule").flashAttr("chosenCopyFrom", myScheduleId)
                        .flashAttr("errors", List.of("You already have a schedule called my schedule.")))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("value=\"my schedule\"")
                .containsPattern("value=\"" + myScheduleId + "\"\\s+selected=\"selected\">Copy of My schedule")
                .contains("You already have a schedule called my schedule.");
    }

    // Text instead of a schedule id is treated like an id that isn't the student's
    @Test
    void copyFromThatIsntANumberIsRejected() throws Exception {
        mockMvc.perform(post("/student/schedules").param("name", "Morning labs").param("copyFrom", "abc")
                       .session(sessionFor(jack)))
               .andExpect(flash().attribute("errors", List.of("The schedule to copy isn't one of your schedules.")));

        assertThat(scheduleNames(jack)).isEmpty();
    }

    @Test
    void renamingASchedule() throws Exception {
        scheduleListService.create(jack, "My schedule", null);

        mockMvc.perform(post("/student/schedules/{id}/rename", scheduleId(jack, "My schedule"))
                       .param("name", "Afternoon labs").session(sessionFor(jack)))
               .andExpect(redirectedUrl("/student/schedules"))
               .andExpect(flash().attribute("message", "Renamed My schedule to Afternoon labs."));

        assertThat(scheduleNames(jack)).containsExactly("Afternoon labs");
    }

    @Test
    void deletingASchedule() throws Exception {
        scheduleListService.create(jack, "My schedule", null);
        scheduleListService.create(jack, "Morning labs", null);

        mockMvc.perform(post("/student/schedules/{id}/delete", scheduleId(jack, "Morning labs"))
                       .session(sessionFor(jack)))
               .andExpect(flash().attribute("message", "Deleted Morning labs."));

        assertThat(scheduleNames(jack)).containsExactly("My schedule");
    }

    @Test
    void makingAnotherScheduleMain() throws Exception {
        scheduleListService.create(jack, "My schedule", null);
        scheduleListService.create(jack, "Morning labs", null);

        mockMvc.perform(post("/student/schedules/{id}/main", scheduleId(jack, "Morning labs"))
                       .session(sessionFor(jack)))
               .andExpect(flash().attribute("message", "Morning labs is now your main schedule."));

        assertThat(scheduleNames(jack)).containsExactly("Morning labs", "My schedule");
    }

    // After switching, My schedule shows the new main schedule
    @Test
    void myScheduleShowsTheNewMainSchedule() throws Exception {
        scheduleListService.create(jack, "My schedule", null);
        scheduleListService.create(jack, "Morning labs", null);
        scheduleListService.makeMain(jack, scheduleId(jack, "Morning labs"));

        mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(jack)))
               .andExpect(content().string(containsString("<span>Morning labs</span>")));
    }

    // Changing the id in the form finds nothing, and Finn's schedules stay as they were
    @Test
    void cantChangeAnotherStudentsSchedules() throws Exception {
        scheduleListService.create(finn, "Finn's schedule", null);
        scheduleListService.create(finn, "Finn's morning labs", null);
        Long id = scheduleId(finn, "Finn's morning labs");
        MockHttpSession session = sessionFor(jack);

        mockMvc.perform(post("/student/schedules/{id}/rename", id).param("name", "Mine").session(session))
               .andExpect(flash().attribute("errors", List.of("That schedule isn't one of yours.")));
        mockMvc.perform(post("/student/schedules/{id}/main", id).session(session))
               .andExpect(flash().attribute("errors", List.of("That schedule isn't one of yours.")));
        mockMvc.perform(post("/student/schedules/{id}/delete", id).session(session))
               .andExpect(flash().attribute("errors", List.of("That schedule isn't one of yours.")));
        mockMvc.perform(post("/student/schedules").param("name", "Stolen").param("copyFrom", id.toString())
                       .session(session))
               .andExpect(flash().attribute("errors", List.of("The schedule to copy isn't one of your schedules.")));

        assertThat(scheduleNames(finn)).containsExactly("Finn's schedule", "Finn's morning labs");
        assertThat(scheduleNames(jack)).isEmpty();
    }

    @Test
    void signedOutChangesGoToLogin() throws Exception {
        mockMvc.perform(post("/student/schedules").param("name", "Morning labs"))
               .andExpect(redirectedUrl("/login"));
    }

    // -- Getting here --

    @Test
    void homeAndMyScheduleLinkToMySchedules() throws Exception {
        mockMvc.perform(get("/student/home").session(sessionFor(jack)))
               .andExpect(content().string(containsString("href=\"/student/schedules\"")));
        mockMvc.perform(get("/student/schedule/next-semester").session(sessionFor(jack)))
               .andExpect(content().string(containsString("href=\"/student/schedules\"")));
    }
}
