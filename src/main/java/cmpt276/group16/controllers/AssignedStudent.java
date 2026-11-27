package cmpt276.group16.controllers;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import jakarta.servlet.http.HttpSession;

// Finds a student the signed-in advisor is assigned to, for the advisor's pages about that student.
// The assignment is checked before anything about the student is loaded. Another advisor's student and an id
// that doesn't exist get the same 403, so the answer doesn't even reveal which students exist
@Component
public class AssignedStudent {

    public static final String NOT_ASSIGNED = "That student isn't assigned to you.";

    private final UserRepository userRepository;
    private final AdvisorAssignmentRepository advisorAssignmentRepository;
    private final StudentProfileRepository studentProfileRepository;

    public AssignedStudent(UserRepository userRepository, AdvisorAssignmentRepository advisorAssignmentRepository,
            StudentProfileRepository studentProfileRepository) {
        this.userRepository = userRepository;
        this.advisorAssignmentRepository = advisorAssignmentRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    // Throws a 403 unless the signed-in user is an advisor assigned to this student.
    // AuthInterceptor already keeps everyone but advisors off /advisor/**, but the role is checked again here,
    // so a page outside /advisor/ can't use this to reach a student
    public StudentProfile find(HttpSession session, Long studentId) {
        Optional<User> advisor = SessionUser.user(session, userRepository);
        if (advisor.isEmpty() || advisor.get().getRole() != Role.ADVISOR
                || !advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(advisor.get().getId(), studentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_ASSIGNED);
        }
        return studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, NOT_ASSIGNED));
    }
}
