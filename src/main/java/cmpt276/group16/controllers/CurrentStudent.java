package cmpt276.group16.controllers;

import java.util.Optional;

import org.springframework.stereotype.Component;

import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import jakarta.servlet.http.HttpSession;

// find the logged-in student's profile, it will be empty if nobody is logged in or if the user isnt a student
@Component
public class CurrentStudent {

    // Session key that login stores the User under
    public static final String SESSION_USER = "user";

    private final StudentProfileRepository studentProfileRepository;

    public CurrentStudent(StudentProfileRepository studentProfileRepository) {
        this.studentProfileRepository = studentProfileRepository;
    }

    public Optional<StudentProfile> find(HttpSession session) {
        if (!(session.getAttribute(SESSION_USER) instanceof User user) || user.getRole() != Role.STUDENT) {
            return Optional.empty();
        }
        return studentProfileRepository.findById(user.getId());
    }
}
