package cmpt276.group16.controllers;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import jakarta.servlet.http.HttpSession;

// Finds the signed-in student's profile. Empty if nobody is signed in or the user isn't a student
// (AuthInterceptor already sends those users away before they reach a student page).
// The user is loaded from the database, so a role changed since login is already taken into account
@Component
public class CurrentStudent {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;

    public CurrentStudent(StudentProfileRepository studentProfileRepository, UserRepository userRepository) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
    }

    public Optional<StudentProfile> find(HttpSession session) {
        Optional<User> user = SessionUser.user(session, userRepository);
        if (user.isEmpty() || user.get().getRole() != Role.STUDENT) {
            return Optional.empty();
        }
        // A student without a profile can't be sent to /login, because /login sends a logged-in student
        // back to /student/home and the two would redirect to each other forever
        return Optional.of(studentProfileRepository.findById(user.get().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Your student profile hasn't been set up yet.")));
    }
}
