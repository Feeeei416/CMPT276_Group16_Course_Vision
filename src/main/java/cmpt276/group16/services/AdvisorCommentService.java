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

    // Saves the comment at the clock's current time
    @Transactional
    public void add(User advisor, StudentProfile student, String body) {
        commentRepository.save(new AdvisorComment(student, advisor, body, Instant.now(clock)));
    }
}
