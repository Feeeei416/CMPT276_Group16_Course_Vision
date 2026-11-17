package cmpt276.group16.models;

import jakarta.persistence.*;

// A student's schedule for one upcoming term. A student can have several per term,
// // each one with its own name; the main one is shown on My Schedule page
@Entity
@Table(name = "next_semester_schedule", uniqueConstraints = @UniqueConstraint(columnNames = { "student_id",
        "term_id", "name" }))
public class NextSemesterSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many schedules belong to one student (one per term)
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    // The term this schedule is for, one per student per term
    @ManyToOne
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    public NextSemesterSchedule(StudentProfile student, Term term, String name, boolean main) {
        this.student = student;
        this.term = term;
        this.name = name;
        this.main = main;
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public Term getTerm() {
        return term;
    }

    protected NextSemesterSchedule() {
    }

    public String getName() {
        return name;
    }

    public boolean isMain() {
        return main;
    }
}
