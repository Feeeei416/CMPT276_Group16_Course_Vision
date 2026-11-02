package cmpt276.group16.controllers;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import cmpt276.group16.models.AdvisorAssignment;
import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import jakarta.servlet.http.HttpSession;

/**
 * Shows the home page for each role.
 * AuthInterceptor runs first, so in these methods the user is always logged in
 * and has the role that matches the URL.
 */
@Controller
public class HomeController {

    private final UserRepository userRepository;
    private final AdvisorAssignmentRepository advisorAssignmentRepository;

    public HomeController(UserRepository userRepository,
                          AdvisorAssignmentRepository advisorAssignmentRepository) {
        this.userRepository = userRepository;
        this.advisorAssignmentRepository = advisorAssignmentRepository;
    }

    /** GET /advisor/home : home page for course advisors, listing the students they support. */
    @GetMapping("/advisor/home")
    public String getAdvisorHome(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        // Only this advisor's assignments, so an advisor never sees other advisors' students
        List<StudentProfile> students = advisorAssignmentRepository
                .findByAdvisorIdOrderByStudentUserDisplayNameAsc(user.getId())
                .stream()
                .map(AdvisorAssignment::getStudent)
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("students", students);
        return "advisor/home";
    }

    /** GET /it/home : home page for IT managers, listing every account. */
    @GetMapping("/it/home")
    public String getITManagerHome(Model model, HttpSession session) {
        User user = (User) session.getAttribute(LoginController.SESSION_USER);

        // Accounts only (name, email, role); IT managers never see students' coursework
        model.addAttribute("user", user);
        model.addAttribute("users", userRepository.findAllByOrderByRoleAscDisplayNameAsc());
        return "it/home";
    }
}