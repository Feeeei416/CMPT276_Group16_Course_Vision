package cmpt276.group16.models;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "advisor_assignments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"advisor_id", "student_id"}))
public class AdvisorAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many assignments can point at the same advisor
    @ManyToOne
    @JoinColumn(name = "advisor_id", nullable = false)
    private User advisor;

    // Points at student_profiles, so only students can be assigned
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(nullable = false)
    private Instant assignedAt;

    protected AdvisorAssignment() {}

    public AdvisorAssignment(User advisor, StudentProfile student) {
        if (advisor.getRole() != Role.ADVISOR) {
            throw new IllegalArgumentException("Only advisors can be assigned to students");
        }
        this.advisor = advisor;
        this.student = student;
    }

    // Runs just before the first save, so every assignment gets a time
    @PrePersist
    void beforeFirstSave() {
        this.assignedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getAdvisor() {
        return advisor;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
