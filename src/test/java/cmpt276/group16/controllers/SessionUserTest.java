package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import cmpt276.group16.models.*;

// A plain unit test: SessionUser only reads and writes the session, so no database or Spring is needed
class SessionUserTest {
    
    // A user as if already saved, with the given id
    private static User savedUser(long id) {
        User user = new User("test@email.ca", "hash", "testUser", Role.STUDENT);
        user.setId(id);
        return user;
    }

    // After signing in, the session gives back that user's id
    @Test
    void signInStoresTheUsersId() {
        MockHttpSession session = new MockHttpSession();
        User user = savedUser(5);

        SessionUser.signIn(session, user);

        assertThat(SessionUser.id(session)).contains(5L);
    }

    // Only the id is kept, not the User object, so a changed name or role isn't stuck in the session
    @Test
    void sessionHoldsNoUserObject() {
        MockHttpSession session = new MockHttpSession();
        User user = savedUser(5);

        SessionUser.signIn(session, user);

        assertThat(SessionUser.id(session)).contains(5L);
        for (String name : Collections.list(session.getAttributeNames())) {
            assertThat(session.getAttribute(name)).isNotInstanceOf(User.class);
        }
    }

    // A new session means nobody is signed in
    @Test
    void newSessionHasNoId() {
        MockHttpSession session = new MockHttpSession();

        assertThat(SessionUser.id(session)).isEmpty();
    }

    // The interceptor may pass the null from request.getSession(false)
    @Test
    void noSessionHasNoId() {
        assertThat(SessionUser.id(null)).isEmpty();
    }

    // Anything else under the key, like an old or broken value, reads as signed out
    @Test
    void valueThatIsntAnIdIsIgnored() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionUser.SESSION_USER_ID, "12");
        
        assertThat(SessionUser.id(session)).isEmpty();
    }

    // Signing in as someone else replaces the id instead of keeping the first one
    @Test
    void signingInAgainReplacesTheId() {
        MockHttpSession session = new MockHttpSession();
        User user1 = savedUser(1);
        User user2 = savedUser(2);

        SessionUser.signIn(session, user1);
        SessionUser.signIn(session, user2);

        assertThat(SessionUser.id(session)).contains(2L);
    }

    // A user who hasn't been saved has no id, so signing them in is a mistake in the caller
    @Test
    void userWithoutAnIdCantSignIn() {
        MockHttpSession session = new MockHttpSession();
        User user = new User("test1@email.ca", "hash", "testUser1", Role.STUDENT);

        assertThatThrownBy(() -> SessionUser.signIn(session, user))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
