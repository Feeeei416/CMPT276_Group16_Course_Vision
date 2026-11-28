package cmpt276.group16.services;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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
    public static final String NOT_YOURS = "You can only delete your own comments.";

    private final AdvisorCommentRepository commentRepository;
    private final Clock clock;

    public AdvisorCommentService(AdvisorCommentRepository commentRepository, Clock clock) {
        this.commentRepository = commentRepository;
        this.clock = clock;
    }

    // A comment the way a page shows it, for example "Alex" and "Nov 25, 2026".
    // byViewer is true when the person looking at the page wrote it, so it gets a Delete button
    public record CommentRow(Long id, String advisorName, String date, String body, boolean byViewer) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    // One student's comments, newest first
    @Transactional(readOnly = true)
    public List<AdvisorComment> commentsFor(StudentProfile student) {
        return commentRepository.findByStudentOrderByCreatedAtDescIdDesc(student);
    }

    // The same comments, ready for a page. The date is in the clock's time zone (Vancouver), since Render's
    // servers run on UTC and an evening comment would show the next day's date.
    // viewerId is the signed-in user's id, or null when nobody's comments can be deleted from the page
    @Transactional(readOnly = true)
    public List<CommentRow> rowsFor(StudentProfile student, Long viewerId) {
        return commentsFor(student).stream()
                .map(comment -> new CommentRow(comment.getId(), comment.getAdvisor().getDisplayName(),
                        DATE.format(comment.getCreatedAt().atZone(clock.getZone())), comment.getBody(),
                        comment.getAdvisor().getId().equals(viewerId)))
                .toList();
    }

    // Saves the comment at the clock's current time. Returns why it wasn't saved, or an empty list when it was
    @Transactional
    public List<String> add(User advisor, StudentProfile student, String text) {
        String body = clean(text);
        List<String> problems = problemsWith(body);
        if (problems.isEmpty()) {
            commentRepository.save(new AdvisorComment(student, advisor, body, Instant.now(clock)));
        }
        return problems;
    }

    // Deletes one of the advisor's own comments on this student's report. Returns why it wasn't deleted, or an empty
    // list when it was. Another advisor's comment, a comment on a different student, and an id that doesn't exist
    // all get the same answer
    @Transactional
    public List<String> delete(User advisor, StudentProfile student, Long commentId) {
        long deleted = commentRepository.deleteByIdAndStudentAndAdvisor(commentId, student, advisor);
        return deleted == 0 ? List.of(NOT_YOURS) : List.of();
    }

    // A browser counts a line break in a text box as one character for maxlength, but sends it as \r\n.
    // Turning it back into \n makes the count here match the one the advisor saw.
    // Blank lines and spaces at either end are dropped
    private static String clean(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').strip();
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
