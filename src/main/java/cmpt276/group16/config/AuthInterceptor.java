package cmpt276.group16.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.servlet.HandlerInterceptor;

import cmpt276.group16.controllers.*;
import cmpt276.group16.models.*;

/**
 * Checks login and role before a request reaches a controller.
 * Registered in WebConfig for /student/**, /advisor/** and /it/**.
 */
public class AuthInterceptor implements HandlerInterceptor{
    /**
     * Runs before the controller.
     * Returns true to let the request through, or false after redirecting to /login.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
    throws Exception {
        // Not logged in: no session yet.
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.sendRedirect("/login");
            return false;
        }

        // Not logged in: session exists but has no user (e.g. after logout or expiry)
        User user = (User) session.getAttribute(LoginController.SESSION_USER);
        if (user == null) {
            response.sendRedirect("/login");
            return false;
        }

        // Logged in, but the URL belongs to another role.
        // Each role may only open pages under its own prefix。
        String path = request.getRequestURI();
        boolean allowed = switch (user.getRole()) {
            case STUDENT    -> path.startsWith("/student/");
            case ADVISOR    -> path.startsWith("/advisor/");
            case IT_MANAGER -> path.startsWith("/it/");
        };
        
        // Logged in, but the page belongs to another role: send them to their own home page
        if (!allowed) {
            response.sendRedirect(LoginController.homePath(user.getRole()));
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
