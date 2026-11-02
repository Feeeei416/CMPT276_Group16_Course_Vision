package cmpt276.group16.controllers;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import cmpt276.group16.models.AdvisorAssignment;
import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// Loads only HomeController and the web layer; the repositories are mocks
@WebMvcTest(HomeController.class)
public class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Mocks return an empty list unless a test says otherwise
    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AdvisorAssignmentRepository advisorAssignmentRepository;

    // The password hash is never checked on these pages, so any text works
    private final User student = new User("student@test.com", "hash", "Test Student", Role.STUDENT);
    private final User advisor = new User("advisor@test.com", "hash", "Test Advisor", Role.ADVISOR);
    private final User itManager = new User("it@test.com", "hash", "Test IT Manager", Role.IT_MANAGER);

    // A student assigned to the advisor, built the same way the app builds one
    private StudentProfile studentProfile(String email, String name, Program program, int cohortYear) {
        return new StudentProfile(new User(email, "hash", name, Role.STUDENT), program, cohortYear);
    }

    // #9 A home page redirects to /login when not logged in
    @Test
    void advisorHome_whenNotLoggedIn_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/advisor/home"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login"));
    }

    // #10 Each role can open its own home page, and the page receives the user
    // (the student home is StudentHomeController, tested in StudentHomeControllerTest)
    @Test
    void advisorHome_asAdvisor_showsPage() throws Exception {
        mockMvc.perform(get("/advisor/home").sessionAttr(LoginController.SESSION_USER, advisor))
               .andExpect(status().isOk())
               .andExpect(view().name("advisor/home"))
               .andExpect(model().attribute("user", advisor));
    }

    @Test
    void itHome_asItManager_showsPage() throws Exception {
        mockMvc.perform(get("/it/home").sessionAttr(LoginController.SESSION_USER, itManager))
               .andExpect(status().isOk())
               .andExpect(view().name("it/home"))
               .andExpect(model().attribute("user", itManager));
    }

    // #11 A user who opens another role's page is sent to their own home page
    @Test
    void advisorHome_asStudent_redirectsToStudentHome() throws Exception {
        mockMvc.perform(get("/advisor/home").sessionAttr(LoginController.SESSION_USER, student))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/student/home"));
    }

    @Test
    void itHome_asAdvisor_redirectsToAdvisorHome() throws Exception {
        mockMvc.perform(get("/it/home").sessionAttr(LoginController.SESSION_USER, advisor))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/advisor/home"));
    }

    // #12 Protected pages tell the browser not to cache them
    @Test
    void advisorHome_asAdvisor_setsNoCacheHeaders() throws Exception {
        mockMvc.perform(get("/advisor/home").sessionAttr(LoginController.SESSION_USER, advisor))
               .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
               .andExpect(header().string("Pragma", "no-cache"))
               .andExpect(header().string("Expires", "0"));
    }

    // Advisor home: lists the advisor's assigned students with program and cohort
    @Test
    void advisorHome_listsAssignedStudents() throws Exception {
        advisor.setId(10L);
        StudentProfile priya = studentProfile("priya@example.com", "Priya", Program.COMPUTING_SCIENCE, 2024);
        StudentProfile marcus = studentProfile("marcus@example.com", "Marcus", Program.COMPUTER_ENGINEERING, 2023);
        when(advisorAssignmentRepository.findByAdvisorIdOrderByStudentUserDisplayNameAsc(10L))
                .thenReturn(List.of(new AdvisorAssignment(advisor, marcus), new AdvisorAssignment(advisor, priya)));

        mockMvc.perform(get("/advisor/home").sessionAttr(LoginController.SESSION_USER, advisor))
               .andExpect(status().isOk())
               .andExpect(model().attribute("students", List.of(marcus, priya)))
               .andExpect(content().string(containsString("Priya")))
               .andExpect(content().string(containsString("marcus@example.com")))
               .andExpect(content().string(containsString("Computing Science")))
               .andExpect(content().string(containsString("Computer Engineering")))
               .andExpect(content().string(not(containsString("No students are assigned to you yet."))));
    }

    // Advisor home: only asks for this advisor's own students
    @Test
    void advisorHome_looksUpOnlyTheLoggedInAdvisorsStudents() throws Exception {
        advisor.setId(10L);

        mockMvc.perform(get("/advisor/home").sessionAttr(LoginController.SESSION_USER, advisor))
               .andExpect(status().isOk());

        verify(advisorAssignmentRepository).findByAdvisorIdOrderByStudentUserDisplayNameAsc(10L);
    }

    // Advisor home: an advisor with no students sees a message instead of an empty table
    @Test
    void advisorHome_withNoStudents_showsEmptyMessage() throws Exception {
        mockMvc.perform(get("/advisor/home").sessionAttr(LoginController.SESSION_USER, advisor))
               .andExpect(status().isOk())
               .andExpect(model().attribute("students", List.of()))
               .andExpect(content().string(containsString("No students are assigned to you yet.")));
    }

    // IT home: lists every account with a readable role
    @Test
    void itHome_listsAllAccounts() throws Exception {
        when(userRepository.findAllByOrderByRoleAscDisplayNameAsc())
                .thenReturn(List.of(advisor, itManager, student));

        mockMvc.perform(get("/it/home").sessionAttr(LoginController.SESSION_USER, itManager))
               .andExpect(status().isOk())
               .andExpect(model().attribute("users", List.of(advisor, itManager, student)))
               .andExpect(content().string(containsString("3 accounts")))
               .andExpect(content().string(containsString("Test Advisor")))
               .andExpect(content().string(containsString("student@test.com")))
               .andExpect(content().string(containsString("IT manager")));
    }

    // IT home: password hashes never appear on the page
    @Test
    void itHome_doesNotShowPasswordHashes() throws Exception {
        User withHash = new User("priya@example.com", "$2a$10$secretHashValue", "Priya", Role.STUDENT);
        when(userRepository.findAllByOrderByRoleAscDisplayNameAsc()).thenReturn(List.of(itManager, withHash));

        mockMvc.perform(get("/it/home").sessionAttr(LoginController.SESSION_USER, itManager))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("priya@example.com")))
               .andExpect(content().string(not(containsString("secretHashValue"))));
    }
}