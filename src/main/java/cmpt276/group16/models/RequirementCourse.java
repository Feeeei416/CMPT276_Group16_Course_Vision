package cmpt276.group16.models;

import jakarta.persistence.*;

// A course named by a requirement, for example MATH 151 in "1 of MATH 150, 151, 154, 157".
// Stored as a subject and number rather than a link to courses, because the courses table only has
// the courses that were imported, and a requirement can name one SFU hasn't offered recently
@Entity
@Table(name = "requirement_courses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"requirement_id", "subject", "course_number"}))
public class RequirementCourse {

    // What the course does in its requirement
    public enum Use {
        // counts toward the requirement
        LISTED,
        // counts, and must be one of the courses used, like CMPT 307 in Breadth
        REQUIRED,
        // never counts, like CMPT 415 in "CMPT 400 and above, not CMPT 415"
        EXCLUDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many courses belong to one requirement
    @ManyToOne
    @JoinColumn(name = "requirement_id", nullable = false)
    private Requirement requirement;

    // Uppercase, like courses.subject, for example "CMPT"
    @Column(nullable = false)
    private String subject;

    // Like courses.course_number, for example "105W"
    @Column(name = "course_number", nullable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_use", nullable = false)
    private Use use;

    // AREAS only: the area the course belongs to, for example "Artificial Intelligence"
    private String area;

    protected RequirementCourse() {}

    public RequirementCourse(Requirement requirement, String subject, String number, Use use) {
        this.requirement = requirement;
        this.subject = Course.normalizeSubjectOrNumber(subject);
        this.number = Course.normalizeSubjectOrNumber(number);
        this.use = use;
    }

    public Long getId() {
        return id;
    }

    public Requirement getRequirement() {
        return requirement;
    }

    public String getSubject() {
        return subject;
    }

    public String getNumber() {
        return number;
    }

    // For example "CMPT 105W"
    public String getCode() {
        return subject + " " + number;
    }

    public Use getUse() {
        return use;
    }

    public void setUse(Use use) {
        this.use = use;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }
}
