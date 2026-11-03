package cmpt276.group16.controllers;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.PasswordRules;
import cmpt276.group16.services.RegistrationService;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.crypto.password.PasswordEncoder;


// Lets any logged-in user view and edit their own profile.
// AuthInterceptor runs first, so there is always a logged-in user here
@Controller
public class ProfileController {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, StudentProfileRepository studentProfileRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {
        return showProfilePage(model, currentUser(session), List.of(), List.of());
    }

    // Only these three fields are read, so email and role can't be changed by editing the form
    @PostMapping("/profile")
    public String updateProfile(@RequestParam(defaultValue = "") String displayName,
                                @RequestParam(required = false) String program,
                                @RequestParam(required = false) String cohortYear,
                                HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        List<String> errors = new ArrayList<>();

        String newName = displayName.trim();
        if (newName.isEmpty()) {
            errors.add("Display Name is required.");
        } else if (newName.length() > 255) {
            errors.add("Display Name must be at most 255 characters.");
        }

        // Only students have a profile with a program and entry year
        StudentProfile profile = studentProfileRepository.findById(user.getId()).orElse(null);
        Program newProgram = RegistrationService.parseProgram(program);
        Integer newCohortYear = RegistrationService.parseCohortYear(cohortYear);
        if (profile != null && newProgram == null) {
            errors.add("Choose a program.");
        }
        if (profile != null && newCohortYear == null) {
            errors.add("Choose an entry year.");
        }

        // Nothing is saved unless every field is valid
        if (!errors.isEmpty()) {
            return showProfilePage(model, user, errors, List.of());
        }

        user.setDisplayName(newName);
        userRepository.save(user);
        if (profile != null) {
            profile.setProgram(newProgram);
            profile.setCohortYear(newCohortYear);
            studentProfileRepository.save(profile);
        }

        // Login keeps a copy of the user in the session, so replace it or pages keep showing the old name
        session.setAttribute(LoginController.SESSION_USER, user);

        redirectAttributes.addFlashAttribute("message", "Your profile was saved.");
        return "redirect:/profile";
    }

    // Passwords aren't trimmed, since spaces can be part of a password
    @PostMapping("/profile/password")
    public String changePassword(@RequestParam(defaultValue = "") String currentPassword,
                                 @RequestParam(defaultValue = "") String newPassword,
                                 @RequestParam(defaultValue = "") String confirmPassword,
                                 HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        List<String> errors = new ArrayList<>();

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            errors.add("Current password is incorrect.");
        }
        // The same rules registration uses, so the two can't drift apart
        errors.addAll(PasswordRules.problems(newPassword));
        if (!newPassword.equals(confirmPassword)) {
            errors.add("New passwords do not match.");
        }

        // The old password keeps working unless every check passes
        if (!errors.isEmpty()) {
            return showProfilePage(model, user, List.of(), errors);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        session.setAttribute(LoginController.SESSION_USER, user);

        redirectAttributes.addFlashAttribute("message", "Your password was changed.");
        return "redirect:/profile";
    }

    // Loads the user from the database, since the session copy can be out of date
    private User currentUser(HttpSession session) {
        User sessionUser = (User) session.getAttribute(LoginController.SESSION_USER);
        return userRepository.findById(sessionUser.getId()).orElseThrow();
    }

    // errors belong to the details form and passwordErrors to the password form, so each shows next to its own form
    private String showProfilePage(Model model, User user, List<String> errors, List<String> passwordErrors) {
        model.addAttribute("user", user);
        model.addAttribute("roleName", roleName(user.getRole()));
        model.addAttribute("homePath", LoginController.homePath(user.getRole()));
        model.addAttribute("studentProfile", studentProfileRepository.findById(user.getId()).orElse(null));
        model.addAttribute("programs", Program.values());
        model.addAttribute("cohortYears", StudentProfile.SUPPORTED_COHORT_YEARS);
        model.addAttribute("errors", errors);
        model.addAttribute("passwordErrors", passwordErrors);
        model.addAttribute("minPasswordLength", PasswordRules.MIN_LENGTH);
        return "profile/profile";
    }

    private static String roleName(Role role) {
        return switch (role) {
            case STUDENT -> "Student";
            case ADVISOR -> "Advisor";
            case IT_MANAGER -> "IT manager";
        };
    }
}
