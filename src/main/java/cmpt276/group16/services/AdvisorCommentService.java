package cmpt276.group16.services;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.AdvisorComment;
import cmpt276.group16.models.AdvisorCommentRepository;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.User;

// #72: the comments advisors leave on a student's Graduation Report.
// The caller checks that the advisor is assigned to the student first (AssignedStudent does on the advisor's pages)
@Service
public class AdvisorCommentService {

    public static final String EMPTY = "A comment can't be empty.";

    private final AdvisorCommentRepository commentRepository;
    private final Clock clock;

    public AdvisorCommentService(AdvisorCommentRepository commentRepository, Clock clock) {
        this.commentRepository = commentRepository;
        this.clock = clock;
    }

    // One student's comments, newest first
    @Transactional(readOnly = true)
    public List<AdvisorComment> commentsFor(StudentProfile student) {
        return commentRepository.findByStudentOrderByCreatedAtDescIdDesc(student);
    }

    // Saves the comment at the clock's current time. Returns why it wasn't saved, or an empty list when it was.
    // Blank lines and spaces at either end are dropped
    @Transactional
    public List<String> add(User advisor, StudentProfile student, String text) {
        String body = text == null ? "" : text.strip();
        List<String> problems = problemsWith(body);
        if (problems.isEmpty()) {
            commentRepository.save(new AdvisorComment(student, advisor, body, Instant.now(clock)));
        }
        return problems;
    }

    private static List<String> problemsWith(String body) {
        if (body.isEmpty()) {
            return List.of(EMPTY);
        }
        if (body.length() > AdvisorComment.MAX_LENGTH) {
            return List.of("A comment can be at most " + AdvisorComment.MAX_LENGTH + " characters (this one has "
                    + body.length() + ").");
        }
        return List.of();
    }
}
