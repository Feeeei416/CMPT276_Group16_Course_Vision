package cmpt276.group16.config;

import java.util.Optional;

import org.springframework.web.servlet.HandlerInterceptor;

import cmpt276.group16.controllers.SessionUser;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Checks login and role before a request reaches a controller.
 * Registered in WebConfig for /student/**, /advisor/**, /it/** and /profile.
 * The session only holds the user's id, so the user is loaded from the database on each request.
 */
public class AuthInterceptor implements HandlerInterceptor {
    private final UserRepository users;

    public AuthInterceptor(UserRepository users) {
        this.users = users;
    }
    
    /**
     * Runs before the controller.
     * Returns true to let the request through, or false after redirecting.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // Loaded from the database on every request rather than kept in the session, so a changed role
        // applies on the next page without logging in again, and a deleted user is signed out right away
        HttpSession session = request.getSession(false);
        Optional<User> signedIn = SessionUser.user(session, users);

        // Not signed in: no session, no id in it (after logout or expiry), or the user was deleted.
        // A session left over from a deleted user is cleared, so it can't be used again
        if (signedIn.isEmpty()) {
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect("/login");
            return false;
        }

        User user = signedIn.get();

        // Each role may only open pages under its own prefix.
        String prefix = switch (user.getRole()) {
            case STUDENT    -> "/student/";
            case ADVISOR    -> "/advisor/";
            case IT_MANAGER -> "/it/";
        };

        // Every role has a profile page, so /profile is open to anyone logged in
        String path = request.getRequestURI();
        boolean profilePage = path.equals("/profile") || path.startsWith("/profile/");

        // Logged in, but the URL belongs to another role:
        // send them straight to their own home page.
        if (!profilePage && !path.startsWith(prefix)) {
            response.sendRedirect(prefix + "home");
            return false;
        }

        // Allowed: tell the browser not to cache protected pages,
        // so pressing Back after logout reloads the page and gets checked again.
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        return true;
    }
}