package cmpt276.group16.models;

import jakarta.persistence.*;

@Entity
@Table(name = "next_semester_schedule", uniqueConstraints = @UniqueConstraint(columnNames = { "student_id",
        "term_id" }))
public class NextSemesterSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    public NextSemesterSchedule(StudentProfile student, Term term) {
        this.student = student;
        this.term = term;
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
}
