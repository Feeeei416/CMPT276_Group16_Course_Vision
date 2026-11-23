package cmpt276.group16.controllers;

import java.util.Optional;

import cmpt276.group16.models.*;
import jakarta.servlet.http.HttpSession;

// Reads and writes who is signed in. The session keeps only the user's id; pages load the User
// from the database when they need it, so a changed name or role shows up without logging in again
public class SessionUser {

    // The session key for the signed-in user's id. A new name, so sessions saved before this change
    // (which kept the whole User under "session_user") read as signed out
    public static final String SESSION_USER_ID = "session_user_id";

    // Remembers who is signed in by storing only their id. Callers that start a new login
    // (LoginController, RegisterController) should give it a fresh session first
    public static void signIn(HttpSession session, User user) {
        Long userId = user.getId();
        if (userId == null) {
            throw new IllegalArgumentException("Save the user before signing them in");
        }
        session.setAttribute(SESSION_USER_ID, userId);
        
    }

    // The signed-in user's id, or empty if nobody is signed in
    public static Optional<Long> id(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        if (session.getAttribute(SESSION_USER_ID) instanceof Long userId) {
            return Optional.of(userId);            
        }

        return Optional.empty();
    }

    // The signed-in user, loaded from the database so their name and role are current.
    // Empty if nobody is signed in, or if the user has since been deleted
    public static Optional<User> user(HttpSession session, UserRepository users) {
        Optional<User> user = id(session).flatMap(i -> users.findById(i));
        return user;
    }

    private SessionUser() {}
}
