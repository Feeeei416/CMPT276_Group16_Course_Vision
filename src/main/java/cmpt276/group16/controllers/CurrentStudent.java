package cmpt276.group16.controllers;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import jakarta.servlet.http.HttpSession;

// find the logged-in student's profile, it will be empty if nobody is logged in or if the user isnt a student
// (AuthInterceptor already sends those users to /login before they reach a student page)
@Component
public class CurrentStudent {

    private final StudentProfileRepository studentProfileRepository;

    public CurrentStudent(StudentProfileRepository studentProfileRepository) {
        this.studentProfileRepository = studentProfileRepository;
    }

    public Optional<StudentProfile> find(HttpSession session) {
        if (!(session.getAttribute(LoginController.SESSION_USER) instanceof User user) || user.getRole() != Role.STUDENT) {
            return Optional.empty();
        }
        // A student without a profile can't be sent to /login, because /login sends a logged-in student
        // back to /student/home and the two would redirect to each other forever
        return Optional.of(studentProfileRepository.findById(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Your student profile hasn't been set up yet.")));
    }
}
