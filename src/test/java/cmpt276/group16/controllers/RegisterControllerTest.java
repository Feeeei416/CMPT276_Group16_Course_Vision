package cmpt276.group16.controllers;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cmpt276.group16.models.*;
import cmpt276.group16.services.RegistrationService;

// Loads only RegisterController and the web layer.
// RegistrationService is mocked: its validation rules are tested in RegistrationServiceTest,
// so these tests only check what the controller does with the results.
@WebMvcTest(RegisterController.class)
public class RegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Fake service: each test decides what validate() returns
    @MockitoBean
    private RegistrationService registrationService;

    // #1 GET /register shows the empty form with the dropdown options
    @Test
    void getRegister_whenNotLoggedIn_showsForm() throws Exception {
        mockMvc.perform(get("/register"))
               .andExpect(status().isOk())
               .andExpect(view().name("login/register"))
               .andExpect(model().attribute("errors", empty()))
               .andExpect(model().attributeExists("form", "programs", "cohortYears"));
    }

    // #2 GET /register when already logged in redirects to the user's home page
    @Test
    void getRegister_whenLoggedIn_redirectsToHome() throws Exception {
        User advisor = new User("advisor@test.com", "hash", "Test Advisor", Role.ADVISOR);

        mockMvc.perform(get("/register").sessionAttr(LoginController.SESSION_USER, advisor))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/advisor/home"));
    }

    // #3 POST /register with valid input saves the student and redirects to the login page
    @Test
    void postRegister_valid_savesStudentAndRedirects() throws Exception {
        when(registrationService.validate(any())).thenReturn(List.of());

        mockMvc.perform(post("/register")
                        .param("email", "newstudent@test.com")
                        .param("displayName", "New Student")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("program", "COMPUTING_SCIENCE")
                        .param("cohortYear", "2024"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login?registered"));

        // The form values were converted and passed to the service
        verify(registrationService).createUserAndStudentProfile(
                "newstudent@test.com", "New Student", "password123", Program.COMPUTING_SCIENCE, 2024);
    }

    // #4 POST /register with invalid Confirm Password shows the form again with the errors and typed values,
    // never sends the passwords back, and saves nothing
    @Test
    void postRegister_invalid_showsErrorsWithoutPasswords() throws Exception {
        when(registrationService.validate(any())).thenReturn(List.of("Passwords do not match."));

        mockMvc.perform(post("/register")
                        .param("email", "newstudent@test.com")
                        .param("displayName", "New Student")
                        .param("password", "password123")
                        .param("confirmPassword", "different123")
                        .param("program", "COMPUTING_SCIENCE")
                        .param("cohortYear", "2024"))
               .andExpect(status().isOk())
               .andExpect(view().name("login/register"))
               .andExpect(model().attribute("errors", contains("Passwords do not match.")))
               .andExpect(model().attribute("form", hasEntry("email", "newstudent@test.com")))
               .andExpect(model().attribute("form", hasEntry("displayName", "New Student")))
               .andExpect(model().attribute("form", not(hasKey("password"))))
               .andExpect(model().attribute("form", not(hasKey("confirmPassword"))))
               .andExpect(model().attributeExists("programs", "cohortYears"));

        verify(registrationService, never())
                .createUserAndStudentProfile(anyString(), anyString(), anyString(), any(), anyInt());
    }

    // #5 POST /register when the email is taken between validating and saving shows the duplicate error
    @Test
    void postRegister_emailTakenWhileSaving_showsDuplicateError() throws Exception {
        when(registrationService.validate(any())).thenReturn(List.of());
        when(registrationService.createUserAndStudentProfile(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        mockMvc.perform(post("/register")
                        .param("email", "taken@test.com")
                        .param("displayName", "New Student")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("program", "COMPUTING_SCIENCE")
                        .param("cohortYear", "2024"))
               .andExpect(status().isOk())
               .andExpect(view().name("login/register"))
               .andExpect(model().attribute("errors", contains("An account with this email already exists.")))
               .andExpect(model().attribute("form", not(hasKey("password"))));
    }

    // #6 POST /register ignores a role sent in the form: only students can register
    @Test
    void postRegister_withRoleParam_stillRegistersStudent() throws Exception {
        when(registrationService.validate(any())).thenReturn(List.of());

        mockMvc.perform(post("/register")
                        .param("email", "newstudent@test.com")
                        .param("displayName", "New Student")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("program", "COMPUTING_SCIENCE")
                        .param("cohortYear", "2024")
                        .param("role", "IT_MANAGER"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login?registered"));

        // The save method has no role parameter; RegistrationService always uses Role.STUDENT
        verify(registrationService).createUserAndStudentProfile(
                "newstudent@test.com", "New Student", "password123", Program.COMPUTING_SCIENCE, 2024);
    }
}
