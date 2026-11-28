package cmpt276.group16.models;

import java.time.Instant;

import jakarta.persistence.*;

// #72: a comment an advisor leaves on a student's Graduation Report, for example advice from an appointment.
// Only advisors write them; the student reads them on their report
@Entity
@Table(name = "advisor_comments")
public class AdvisorComment {

    // The most a comment can hold (the column's length)
    public static final int MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Whose report the comment is on
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    // Who wrote it. Kept when the student is later assigned to someone else, so the name stays on the comment
    @ManyToOne
    @JoinColumn(name = "advisor_id", nullable = false)
    private User advisor;

    @Column(nullable = false, length = MAX_LENGTH)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    protected AdvisorComment() {}

    public AdvisorComment(StudentProfile student, User advisor, String body, Instant createdAt) {
        if (advisor.getRole() != Role.ADVISOR) {
            throw new IllegalArgumentException("Only advisors can comment on a student's report");
        }
        this.student = student;
        this.advisor = advisor;
        this.body = body;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public User getAdvisor() {
        return advisor;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
