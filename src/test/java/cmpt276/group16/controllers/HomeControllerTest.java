package cmpt276.group16.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import cmpt276.group16.models.Role;
import cmpt276.group16.models.User;

// Loads only HomeController and the web layer
@WebMvcTest(HomeController.class)
public class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The password hash is never checked on these pages, so any text works
    private final User student = new User("student@test.com", "hash", "Test Student", Role.STUDENT);
    private final User advisor = new User("advisor@test.com", "hash", "Test Advisor", Role.ADVISOR);
    private final User itManager = new User("it@test.com", "hash", "Test IT Manager", Role.IT_MANAGER);

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
}