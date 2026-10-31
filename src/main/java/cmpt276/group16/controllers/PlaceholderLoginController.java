package cmpt276.group16.controllers;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import jakarta.servlet.http.HttpSession;

// TODO: placeholder until the real login story is merged so we can delete this class and login.html at that point
// Logs everyone in as one demo student so the student pages can be built and tested
@Controller
public class PlaceholderLoginController {

    static final String DEMO_EMAIL = "demo.student@example.com";

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public PlaceholderLoginController(UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping({ "/", "/login" })
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String logInAsDemoStudent(HttpSession session) {
        User student = userRepository.findByNormalizedEmail(DEMO_EMAIL).orElseGet(this::createDemoStudent);
        session.setAttribute(CurrentStudent.SESSION_USER, student);
        return "redirect:/student/home";
    }

    @PostMapping("/logout")
    public String logOut(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private User createDemoStudent() {
        User student = userRepository.save(
                new User(DEMO_EMAIL, passwordEncoder.encode("demo-password"), "Demo Student", Role.STUDENT));
        studentProfileRepository.save(new StudentProfile(student, Program.COMPUTER_ENGINEERING, 2024));
        return student;
    }
}
