package cmpt276.group16.controllers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cmpt276.group16.config.PasswordConfig;
import cmpt276.group16.models.*;

// Loads only LoginController and the web layer
@WebMvcTest (LoginController.class)
// @WebMvcTest does not load PasswordConfig by itself, so import the real encoder
@Import(PasswordConfig.class)
public class LoginControllerTest {

    // Same text as LOGIN_ERROR in LoginController
    private static final String LOGIN_ERROR = "Invalid email or password.";

    @Autowired 
    private MockMvc mockMvc;
    
    @Autowired 
    private PasswordEncoder encoder;

    @MockitoBean 
    private UserRepository repo;

    // #1 GET /redirects to the login page
    @Test 
    void rootRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login"));
    }

    // #2 GET /login shows the login page when not logged in
    @Test
    void loginPageShownWhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/login"))
               .andExpect(status().isOk())
               .andExpect(view().name("login/login"));
    }

    // #3 GET /login when already logged in redirects to the student's home page
    @Test 
    void loginPage_whenLoggedIn_redirectsToStudentHome() throws Exception {
        User student = new User("student@test.com", encoder.encode("test123"), "Test Student", Role.STUDENT);

        mockMvc.perform(get("/login").sessionAttr(LoginController.SESSION_USER, student))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/student/home"));
    }

    // #4 POST /login with correct email and password redirects a student to the student home page
    @Test 
    void login_withCorrectPassword_redirectsToStudentHome() throws Exception {
        User user = new User("student@test.com", encoder.encode("test123"), "Test Studnent", Role.STUDENT);
        when(repo.findByNormalizedEmail("student@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/login")
                        .param("email", "student@test.com")
                        .param("password", "test123"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/student/home"))
               .andExpect(request().sessionAttribute(LoginController.SESSION_USER, user));
    }

    // #4 POST /login with correct email and password redirects an advisor to the advisor home page
    @Test 
    void login_withCorrectPassword_redirectsToAdvisorHome() throws Exception {
        User user = new User("advisor@test.com", encoder.encode("test456"), "Test Advisor", Role.ADVISOR);
        when(repo.findByNormalizedEmail("advisor@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/login")
                        .param("email", "advisor@test.com")
                        .param("password", "test456"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/advisor/home"))
               .andExpect(request().sessionAttribute(LoginController.SESSION_USER, user));
    }

    // #4 POST /login with correct email and password redirects an IT manager to the IT home page
    @Test 
    void login_withCorrectPassword_redirectsToITHome() throws Exception {
        User user = new User("it@test.com", encoder.encode("test789"), "Test IT Manager", Role.IT_MANAGER);
        when(repo.findByNormalizedEmail("it@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/login")
                        .param("email", "it@test.com")
                        .param("password", "test789"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/it/home"))
               .andExpect(request().sessionAttribute(LoginController.SESSION_USER, user));
    }

    // #5 POST /login with a wrong password shows the login page with an error and the typed email
    @Test
    void login_withWrongPassword_showsError() throws Exception {
        User user = new User("student@test.com", encoder.encode("test123"), "Test Student", Role.STUDENT);
        when(repo.findByNormalizedEmail("student@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/login")
                        .param("email", "student@test.com")
                        .param("password", "wrongPassword"))
               .andExpect(status().isOk())
               .andExpect(view().name("login/login"))
               .andExpect(model().attribute("error", LOGIN_ERROR))
               .andExpect(model().attribute("email", "student@test.com"))
               .andExpect(request().sessionAttributeDoesNotExist(LoginController.SESSION_USER));
    }

    // #6 POST /login with an unknown email shows the same error as a wrong password
    @Test
    void login_withUnknownEmail_showsSameError() throws Exception {
        when(repo.findByNormalizedEmail("nobody@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/login")
                        .param("email", "nobody@test.com")
                        .param("password", "test123"))
               .andExpect(status().isOk())
               .andExpect(view().name("login/login"))
               .andExpect(model().attribute("error", LOGIN_ERROR))
               .andExpect(model().attribute("email", "nobody@test.com"))
               .andExpect(request().sessionAttributeDoesNotExist(LoginController.SESSION_USER));
    }

    // #7 POST /login with a blank email shows an error without searching the database
    @Test
    void login_withBlankEmail_showsErrorWithoutLookup() throws Exception {
        mockMvc.perform(post("/login")
                        .param("email", "   ")
                        .param("password", "test123"))
               .andExpect(status().isOk())
               .andExpect(view().name("login/login"))
               .andExpect(model().attribute("error", LOGIN_ERROR));

        verify(repo, never()).findByNormalizedEmail(any());
    }
    
    // #7 POST /login with no password field shows an error without searching the database
    @Test
    void login_withMissingPassword_showsErrorWithoutLookup() throws Exception {
        mockMvc.perform(post("/login")
                        .param("email", "student@test.com"))
               .andExpect(status().isOk())
               .andExpect(view().name("login/login"))
               .andExpect(model().attribute("error", LOGIN_ERROR))
               .andExpect(model().attribute("email", "student@test.com"));

        verify(repo, never()).findByNormalizedEmail(any());
    }

    // #8 POST /logout ends the session and redirects to the login page with the logout notice
    @Test
    void logout_invalidatesSessionAndRedirects() throws Exception {
        User student = new User("student@test.com", encoder.encode("test123"), "Test Student", Role.STUDENT);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginController.SESSION_USER, student);

        mockMvc.perform(post("/logout").session(session))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login?logout"));

        assertTrue(session.isInvalid());
    }
}
