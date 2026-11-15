package cmpt276.group16.models;

import java.math.BigDecimal;

import jakarta.persistence.*;

// A course placed in one term of a plan. No section is chosen, since later terms have no times yet
@Entity
@Table(name = "planned_courses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_term_id", "course_id"}))
public class PlannedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many courses belong to one term
    @ManyToOne
    @JoinColumn(name = "plan_term_id", nullable = false)
    private PlanTerm term;

    // The course from the courses table, so the code, title and units always match SFU's data
    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    protected PlannedCourse() {}

    public PlannedCourse(PlanTerm term, Course course) {
        this.term = term;
        this.course = course;
    }

    public Long getId() {
        return id;
    }

    public PlanTerm getTerm() {
        return term;
    }

    public Course getCourse() {
        return course;
    }

    // For example "CMPT 225"
    public String getCode() {
        return course.getSubject() + " " + course.getNumber();
    }

    public String getTitle() {
        return course.getTitle();
    }

    public BigDecimal getUnits() {
        return course.getUnits();
    }
}
