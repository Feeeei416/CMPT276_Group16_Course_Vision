package cmpt276.group16.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.AdvisorAssignment;
import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// Runs once on startup and adds any demo accounts that are missing
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final AdvisorAssignmentRepository advisorAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DataSeeder(UserRepository userRepository,
                      StudentProfileRepository studentProfileRepository,
                      AdvisorAssignmentRepository advisorAssignmentRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.demo-password}") String demoPassword) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.advisorAssignmentRepository = advisorAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    // One transaction
    @Override
    @Transactional
    public void run(String... args) {
        // if no demo password is set, then skip
        if (demoPassword.isBlank()) {
            return;
        }

        User advisor = findOrCreateUser("advisor@example.com", "Demo Advisor", Role.ADVISOR);
        findOrCreateUser("Faye@example.com", "Faye", Role.IT_MANAGER);

        StudentProfile madden = findOrCreateStudent("madden@test.com", "Madden", Program.COMPUTING_SCIENCE, 2024);
        StudentProfile jack = findOrCreateStudent("jack@test.com", "Jack", Program.COMPUTER_ENGINEERING, 2023);

        assignStudent(advisor, madden);
        assignStudent(advisor, jack);
    }

    // Returns the existing account, or saves a new one with the demo password
    private User findOrCreateUser(String email, String displayName, Role role) {
        return userRepository.findByNormalizedEmail(email)
                .orElseGet(() -> userRepository.save(
                        new User(email, passwordEncoder.encode(demoPassword), displayName, role)));
    }

    // Same as findOrCreateUser with student profile
    private StudentProfile findOrCreateStudent(String email, String displayName, Program program, int cohortYear) {
        User user = findOrCreateUser(email, displayName, Role.STUDENT);
        return studentProfileRepository.findById(user.getId())
                .orElseGet(() -> studentProfileRepository.save(new StudentProfile(user, program, cohortYear)));
    }

    private void assignStudent(User advisor, StudentProfile student) {
        if (!advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(advisor.getId(), student.getUserId())) {
            advisorAssignmentRepository.save(new AdvisorAssignment(advisor, student));
        }
    }
}
