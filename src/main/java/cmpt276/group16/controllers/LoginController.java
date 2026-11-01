package cmpt276.group16.controllers;

import cmpt276.group16.models.*;

import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller 
public class LoginController {
    // Key used to store the logged-in user in the session.
    // The interceptor reads the same key, so keep them in sync.
    public static final String SESSION_USER = "session_user";

    // Error message for both "unknown email" and "wrong password"
    private static final String LOGIN_ERROR = "Invalid email or password.";

    @Autowired 
    private UserRepository repo;

    @Autowired 
    private PasswordEncoder encoder;

    /** GET / : redirects to the login page. */
    @GetMapping("/")
    public RedirectView root() {
        return new RedirectView("/login");
    }

    /** 
     * GET /login : shows the login page if not logged in;
     * otherwise redirects to the user's home page.
    */
    @GetMapping("/login") 
    public String getLogin(HttpSession session) {
        User user = (User) session.getAttribute(SESSION_USER);

        if (user == null){
            return "login/login";
        }
        else{
            return homeFor(user.getRole());
        }
    }

    /**
     * POST /login : checks the email and password.
     * On failure, shows the login page again with an error and the typed email;
     * on success, starts a new session and redirects to the user's welcome page.
     */
    @PostMapping("/login")
    public String login(@RequestParam Map<String,String> form, Model model, HttpServletRequest request, HttpSession session){
        // processing login
        String email = form.get("email");
        String password = form.get("password");

        // Check for missing input first: normalizeEmail() and matches() throw on null
        if (email==null || email.isBlank() || password == null ||password.isEmpty()) {
            return showLoginError(model,email);
        }

        // Unknown email and wrong password are handled the same way
        Optional<User> found = repo.findByNormalizedEmail(email);
        if (found.isEmpty()){
            return showLoginError(model,email);
        }
        User user = found.get();
        if (!encoder.matches(password, user.getPasswordHash())) {
            return showLoginError(model,email);
        }
        else {
            // success
            session.invalidate();
            request.getSession().setAttribute(SESSION_USER, user);
            
            return homeFor(user.getRole());
        }
    }

    /** POST /logout : ends the session and returns to the login page. */
    @PostMapping("/logout")
    public String logout(HttpSession session){
        // processing logout
        // Remove the session and everything stored in it, including the logged-in user
        session.invalidate();
            
        return "redirect:/login?logout";
    }

    /** Shows the login page again with the error message and the email the user typed. */
    private String showLoginError(Model model, String email) {
        model.addAttribute("error", LOGIN_ERROR);
        if (email == null){
            model.addAttribute("email", "");
        } 
        else {
            model.addAttribute("email", email.trim());
        }
        return "login/login";
    }

    /** Returns the redirect to the home page for a role. */
    public static String homeFor(Role role) {
        return switch (role) {
            case STUDENT -> "redirect:/student/home";
            case ADVISOR -> "redirect:/advisor/home";
            case IT_MANAGER -> "redirect:/it/home";
        };
    }
}
