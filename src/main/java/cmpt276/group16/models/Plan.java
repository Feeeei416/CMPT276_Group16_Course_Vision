package cmpt276.group16.models;

import jakarta.persistence.*;

// A student's plan for the terms after next semester. #43 lets a student keep several, so each has a name.
// Names are unique per student, which also stops a second "My plan" being created
@Entity
@Table(name = "plans",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "name"}))
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many plans can belong to one student
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @Column(nullable = false)
    private String name;

    // The plan the student works from, and the one the Graduation Report will read
    @Column(name = "is_main", nullable = false)
    private boolean main;

    protected Plan() {}

    public Plan(StudentProfile student, String name, boolean main) {
        this.student = student;
        this.name = name;
        this.main = main;
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudent() {
        return student;
    }

    public String getName() {
        return name;
    }

    public boolean isMain() {
        return main;
    }
}
