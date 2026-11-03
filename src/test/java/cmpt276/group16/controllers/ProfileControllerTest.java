package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import jakarta.persistence.EntityManager;

// Uses the whole app, so AuthInterceptor and the real database are part of every test.
// addFilters = false: Spring Session's filter would swap the test's session for a database one
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User saveUser(String email, Role role) {
        return userRepository.save(new User(email, "hash", "Original Name", role));
    }

    private User saveStudent(String email) {
        User user = saveUser(email, Role.STUDENT);
        studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
        return user;
    }

    // Like a real session: holds its own copy of the user, not the object the database returns
    private MockHttpSession sessionFor(User user) {
        User copy = new User(user.getEmail(), user.getPasswordHash(), user.getDisplayName(), user.getRole());
        copy.setId(user.getId());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginController.SESSION_USER, copy);
        return session;
    }

    // Writes pending changes and clears the cache, so the checks read the database
    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void signedOutVisitorGoesToLogin() throws Exception {
        mockMvc.perform(get("/profile"))
               .andExpect(redirectedUrl("/login"));
    }

    // Every role has a profile, so the interceptor must let all three in
    @ParameterizedTest
    @EnumSource(Role.class)
    void everyRoleCanOpenTheirProfile(Role role) throws Exception {
        User user = role == Role.STUDENT ? saveStudent("someone@example.com") : saveUser("someone@example.com", role);

        mockMvc.perform(get("/profile").session(sessionFor(user)))
               .andExpect(status().isOk())
               .andExpect(view().name("profile/profile"))
               .andExpect(content().string(containsString("someone@example.com")));
    }

    // The Profile link in the shared header points to this page
    @Test
    void headerLinksToProfile() throws Exception {
        User advisor = saveUser("advisor@example.com", Role.ADVISOR);

        mockMvc.perform(get("/profile").session(sessionFor(advisor)))
               .andExpect(content().string(containsString("href=\"/profile\"")));
    }

    @Test
    void onlyStudentsSeeProgramAndYear() throws Exception {
        User student = saveStudent("student@example.com");
        User advisor = saveUser("advisor@example.com", Role.ADVISOR);

        mockMvc.perform(get("/profile").session(sessionFor(student)))
               .andExpect(content().string(containsString("name=\"program\"")));
        mockMvc.perform(get("/profile").session(sessionFor(advisor)))
               .andExpect(content().string(not(containsString("name=\"program\""))));
    }

    @Test
    void savesNewDisplayNameAndUpdatesSession() throws Exception {
        User advisor = saveUser("advisor@example.com", Role.ADVISOR);
        MockHttpSession session = sessionFor(advisor);

        mockMvc.perform(post("/profile").session(session).param("displayName", "  New Name "))
               .andExpect(redirectedUrl("/profile"))
               .andExpect(flash().attribute("message", "Your profile was saved."));
        reloadFromDatabase();

        assertThat(userRepository.findById(advisor.getId()).orElseThrow().getDisplayName()).isEqualTo("New Name");
        User sessionUser = (User) session.getAttribute(LoginController.SESSION_USER);
        assertThat(sessionUser.getDisplayName()).isEqualTo("New Name");
    }

    @Test
    void blankNameShowsErrorAndChangesNothing() throws Exception {
        User advisor = saveUser("advisor@example.com", Role.ADVISOR);

        mockMvc.perform(post("/profile").session(sessionFor(advisor)).param("displayName", "   "))
               .andExpect(status().isOk())
               .andExpect(model().attribute("errors", contains("Display Name is required.")));
        reloadFromDatabase();

        assertThat(userRepository.findById(advisor.getId()).orElseThrow().getDisplayName()).isEqualTo("Original Name");
    }

    @Test
    void studentCanChangeProgramAndYear() throws Exception {
        User student = saveStudent("student@example.com");

        mockMvc.perform(post("/profile").session(sessionFor(student))
                        .param("displayName", "Original Name")
                        .param("program", "COMPUTER_ENGINEERING")
                        .param("cohortYear", "2023"))
               .andExpect(redirectedUrl("/profile"));
        reloadFromDatabase();

        StudentProfile profile = studentProfileRepository.findById(student.getId()).orElseThrow();
        assertThat(profile.getProgram()).isEqualTo(Program.COMPUTER_ENGINEERING);
        assertThat(profile.getCohortYear()).isEqualTo(2023);
    }

    // A bad year is rejected and nothing else on the form is saved either
    @ParameterizedTest
    @ValueSource(strings = {"2022", "2025", "abc", ""})
    void unsupportedYearShowsErrorAndChangesNothing(String year) throws Exception {
        User student = saveStudent("student@example.com");

        mockMvc.perform(post("/profile").session(sessionFor(student))
                        .param("displayName", "New Name")
                        .param("program", "COMPUTER_ENGINEERING")
                        .param("cohortYear", year))
               .andExpect(status().isOk())
               .andExpect(model().attribute("errors", contains("Choose an entry year.")));
        reloadFromDatabase();

        assertThat(userRepository.findById(student.getId()).orElseThrow().getDisplayName()).isEqualTo("Original Name");
        StudentProfile profile = studentProfileRepository.findById(student.getId()).orElseThrow();
        assertThat(profile.getProgram()).isEqualTo(Program.COMPUTING_SCIENCE);
        assertThat(profile.getCohortYear()).isEqualTo(2024);
    }

    // Extra fields added to the form are ignored
    @Test
    void emailAndRoleCantBeChangedThroughTheForm() throws Exception {
        User advisor = saveUser("advisor@example.com", Role.ADVISOR);

        mockMvc.perform(post("/profile").session(sessionFor(advisor))
                        .param("displayName", "Original Name")
                        .param("email", "someone.else@example.com")
                        .param("role", "IT_MANAGER"))
               .andExpect(redirectedUrl("/profile"));
        reloadFromDatabase();

        User saved = userRepository.findById(advisor.getId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("advisor@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.ADVISOR);
    }

    // -- Password change --

    private User saveUserWithPassword(String password) {
        return userRepository.save(new User("advisor@example.com", passwordEncoder.encode(password), "Original Name", Role.ADVISOR));
    }

    private String savedPasswordHash(User user) {
        reloadFromDatabase();
        return userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
    }

    @Test
    void signedOutVisitorCantChangePassword() throws Exception {
        mockMvc.perform(post("/profile/password"))
               .andExpect(redirectedUrl("/login"));
    }

    @Test
    void changesPasswordWhenCurrentOneIsRight() throws Exception {
        User advisor = saveUserWithPassword("old-password");

        mockMvc.perform(post("/profile/password").session(sessionFor(advisor))
                        .param("currentPassword", "old-password")
                        .param("newPassword", "new-password")
                        .param("confirmPassword", "new-password"))
               .andExpect(redirectedUrl("/profile"))
               .andExpect(flash().attribute("message", "Your password was changed."));

        String hash = savedPasswordHash(advisor);
        assertThat(hash).isNotEqualTo("new-password");
        assertThat(passwordEncoder.matches("new-password", hash)).isTrue();
        assertThat(passwordEncoder.matches("old-password", hash)).isFalse();
    }

    // Each row is one bad form: it shows its own error and the old password still works
    @ParameterizedTest
    @CsvSource({
        "wrong-password, new-password, new-password,   Current password is incorrect.",
        "old-password,   new-password, other-password, New passwords do not match.",
        "old-password,   short,        short,          New password must be at least 8 characters."
    })
    void badPasswordFormShowsErrorAndChangesNothing(String current, String newPassword, String confirm,
                                                    String error) throws Exception {
        User advisor = saveUserWithPassword("old-password");

        mockMvc.perform(post("/profile/password").session(sessionFor(advisor))
                        .param("currentPassword", current)
                        .param("newPassword", newPassword)
                        .param("confirmPassword", confirm))
               .andExpect(status().isOk())
               .andExpect(model().attribute("passwordErrors", contains(error)));

        assertThat(passwordEncoder.matches("old-password", savedPasswordHash(advisor))).isTrue();
    }
}
