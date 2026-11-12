package cmpt276.group16.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.AdvisorAssignment;
import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// Runs once on startup and adds any demo accounts and demo courses that are missing
@Component
public class DataSeeder implements CommandLineRunner {

    // A few real SFU courses, so My plan can be tried before the course import (#36) fills the courses table.
    // Titles and units are from the SFU Course Outlines API (Fall 2026). The import updates these rows later,
    // since a course is matched by subject and number
    static final List<DemoCourse> DEMO_COURSES = List.of(
            new DemoCourse("CMPT", "120", "Introduction to Computing Science and Programming I", "3"),
            new DemoCourse("CMPT", "125", "Introduction to Computing Science and Programming II", "3"),
            new DemoCourse("CMPT", "201", "Systems Programming", "4"),
            new DemoCourse("CMPT", "225", "Data Structures and Programming", "3"),
            new DemoCourse("CMPT", "276", "Introduction to Software Engineering", "3"),
            new DemoCourse("CMPT", "295", "Introduction to Computer Systems", "4"),
            new DemoCourse("CMPT", "307", "Data Structures and Algorithms", "3"),
            new DemoCourse("CMPT", "354", "Database Systems I", "3"),
            new DemoCourse("MACM", "101", "Discrete Mathematics I", "3"),
            new DemoCourse("MACM", "201", "Discrete Mathematics II", "3"),
            new DemoCourse("MATH", "151", "Calculus I", "3"),
            new DemoCourse("MATH", "152", "Calculus II", "3"),
            new DemoCourse("ENSC", "251", "Software Design and Analysis for Engineers", "4"),
            new DemoCourse("ENSC", "252", "Fundamentals of Digital Logic & Design", "4"));

    record DemoCourse(String subject, String number, String title, String units) {
    }

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final AdvisorAssignmentRepository advisorAssignmentRepository;
    private final CourseRepository courseRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DataSeeder(UserRepository userRepository,
                      StudentProfileRepository studentProfileRepository,
                      AdvisorAssignmentRepository advisorAssignmentRepository,
                      CourseRepository courseRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.demo-password}") String demoPassword) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.advisorAssignmentRepository = advisorAssignmentRepository;
        this.courseRepository = courseRepository;
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

        DEMO_COURSES.forEach(this::findOrCreateCourse);
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

    // Leaves a course that's already there alone, so details from the import are never overwritten
    private void findOrCreateCourse(DemoCourse demo) {
        if (courseRepository.findBySubjectAndNumber(demo.subject(), demo.number()).isEmpty()) {
            courseRepository.save(new Course(demo.subject(), demo.number(), demo.title(),
                    new BigDecimal(demo.units()), null, null, null));
        }
    }

    private void assignStudent(User advisor, StudentProfile student) {
        if (!advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(advisor.getId(), student.getUserId())) {
            advisorAssignmentRepository.save(new AdvisorAssignment(advisor, student));
        }
    }
}
