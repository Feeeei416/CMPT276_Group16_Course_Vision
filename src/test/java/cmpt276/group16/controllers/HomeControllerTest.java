package cmpt276.group16.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import cmpt276.group16.models.*;

// Loads only LoginController and the web layer
@WebMvcTest (HomeController.class)
// @WebMvcTest does not load regular @Component classes; a web-layer class depends on UploadLimit,
// so import the real one (it only reads spring.servlet.multipart.max-file-size)
@Import(UploadLimit.class)
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

    // #11 A user cannot open another role's home page
    @Test
    void advisorHome_asStudent_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/advisor/home").sessionAttr(LoginController.SESSION_USER, student))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login"));
    }

    @Test
    void itHome_asAdvisor_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/it/home").sessionAttr(LoginController.SESSION_USER, advisor))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login"));
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
