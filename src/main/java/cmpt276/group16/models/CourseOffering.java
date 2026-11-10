package cmpt276.group16.models;

import java.time.Instant;

import jakarta.persistence.*;

@Entity 
@Table(name = "course_offerings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "term_id"}))
public class CourseOffering {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many offerings point at the same course, one for each term
    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // Many offerings point at the same term, one for each course
    @ManyToOne
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    // False means the API was checked and the course is not offered that term,
    // which is different from having no row at all (not checked yet)
    @Column(nullable = false)
    private boolean offered;

    // When this offering was last fetched from the API
    @Column(nullable = false)
    private Instant fetchedAt;

    protected CourseOffering() {}

    public CourseOffering(Course course, Term term, boolean offered) {
        this.course = course;
        this.term = term;
        this.offered = offered;
        this.fetchedAt = Instant.now();
    }

    // Call this every time the course is fetched from the API, even if nothing changed,
    // so fetchedAt always shows when the data was last checked
    public void markFetched() {
        this.fetchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    // No setters for course and term: together they identify the offering
    public Course getCourse() {
        return course;
    }

    public Term getTerm() {
        return term;
    }

    public boolean isOffered() {
        return offered;
    }

    // Lets a re-fetch update whether the course is offered
    public void setOffered(boolean offered) {
        this.offered = offered;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
