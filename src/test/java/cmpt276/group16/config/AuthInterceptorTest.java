package cmpt276.group16.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.controllers.SessionUser;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpSession;

// Uses the real database, so these show the role and the account are read fresh on every request.
// Filters are off so the test's MockHttpSession is used; @Transactional undoes each test's changes
@SpringBootTest 
@AutoConfigureMockMvc(addFilters = false)
@Transactional 
class AuthInterceptorTest {
    
    @Autowired 
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired 
    private PasswordEncoder encoder;

    @Autowired 
    private EntityManager entityManager;

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        SessionUser.signIn(session, user);
        return session;
    }

    // Writes pending changes and clears the cache, so the checks read the database
    private void reloadFromDatabase() {
        entityManager.flush();
        entityManager.clear();
    }

    // An advisor made IT manager is treated as one on their next page, without logging in again
    @Test 
    void roleChangeAppliesOnTheNextRequest() throws Exception {
        User advisor = userRepository.save(new User("test.advisor@example.com", "hash", "Advisor", Role.ADVISOR));
        MockHttpSession session = sessionFor(advisor);

        mockMvc.perform(get("/advisor/home").session(session))
               .andExpect(status().isOk());

        advisor.setRole(Role.IT_MANAGER);
        userRepository.save(advisor);
        reloadFromDatabase();

        mockMvc.perform(get("/advisor/home").session(session))
                .andExpect(redirectedUrl("/it/home"));
    }

    // A deleted user is signed out on their next page, and their old session can't be used again
    @Test 
    void deletedUserIsSentToLogin() throws Exception {
        User advisor = userRepository.save(new User("test.advisor@example.com", "hash", "Advisor", Role.ADVISOR));
        MockHttpSession session = sessionFor(advisor);

        mockMvc.perform(get("/advisor/home").session(session))
               .andExpect(status().isOk());

        userRepository.delete(advisor);
        reloadFromDatabase();

        mockMvc.perform(get("/advisor/home").session(session))
                .andExpect(redirectedUrl("/login"));

        assertThat(session.isInvalid()).isTrue();
    }

    // Sessions saved before #28 kept the whole User under "session_user".
    // That key is gone, so such a session reads as signed out instead of crashing or still working
    @Test 
    void oldSessionWithWholeUserReadsAsSignedOut() throws Exception {
        User advisor = userRepository.save(new User("test.advisor@example.com", "hash", "Advisor", Role.ADVISOR));
        MockHttpSession session = new MockHttpSession();

        // The old key, written as text because the constant no longer exists
        session.setAttribute("session_user", advisor);

        mockMvc.perform(get("/advisor/home").session(session))
           .andExpect(redirectedUrl("/login"));

        assertThat(session.isInvalid()).isTrue();
    }

    // Logging in keeps only the user's id in the new session, not the User itself
    @Test
    void loginStoresOnlyTheId() throws Exception {
        User advisor = userRepository.save(new User("test.advisor@example.com", encoder.encode("test123"), "Advisor", Role.ADVISOR));

        MvcResult result = mockMvc.perform(post("/login").param("email", "test.advisor@example.com").param("password", "test123"))
                .andExpect(redirectedUrl("/advisor/home"))
                .andReturn();
        HttpSession session = result.getRequest().getSession(false);

        assertThat(SessionUser.id(session)).contains(advisor.getId());
        for (String name : Collections.list(session.getAttributeNames())) {
            assertThat(session.getAttribute(name)).isNotInstanceOf(User.class);
        }
    }
}
