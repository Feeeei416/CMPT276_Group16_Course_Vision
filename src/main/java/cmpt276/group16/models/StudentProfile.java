package cmpt276.group16.models;

import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    // Only these cohorts have graduation requirements in the MVP
    public static final List<Integer> SUPPORTED_COHORT_YEARS = List.of(2023, 2024);

    // Same value as the student's user id; @MapsId copies it from the user when saved
    @Id
    private Long userId;

    // Each profile belongs to one user
    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Program program;

    @Column(nullable = false)
    private int cohortYear;

    protected StudentProfile() {}

    public StudentProfile(User user, Program program, int cohortYear) {
        if (user.getRole() != Role.STUDENT) {
            throw new IllegalArgumentException("Only students can have student profile");
        }
        if (!SUPPORTED_COHORT_YEARS.contains(cohortYear)) {
            throw new IllegalArgumentException("Unsupported cohort year:" + cohortYear);
        }
        this.user = user;
        this.program = program;
        this.cohortYear = cohortYear;
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public Program getProgram() {
        return program;
    }

    public int getCohortYear() {
        return cohortYear;
    }
}
