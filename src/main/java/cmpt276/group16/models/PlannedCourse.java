package cmpt276.group16.models;

import jakarta.persistence.*;

// A course placed in one term of a plan. No section is chosen, since later terms have no times yet
@Entity
@Table(name = "planned_courses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_term_id", "subject", "course_number"}))
public class PlannedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many courses belong to one term
    @ManyToOne
    @JoinColumn(name = "plan_term_id", nullable = false)
    private PlanTerm term;

    // for example "CMPT"
    @Column(nullable = false)
    private String subject;

    // for example "225" or "105W"
    @Column(name = "course_number", nullable = false)
    private String number;

    protected PlannedCourse() {}

    public PlannedCourse(PlanTerm term, String subject, String number) {
        this.term = term;
        this.subject = subject;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public PlanTerm getTerm() {
        return term;
    }

    public String getSubject() {
        return subject;
    }

    public String getNumber() {
        return number;
    }

    // For example "CMPT 225"
    public String getCode() {
        return subject + " " + number;
    }
}
