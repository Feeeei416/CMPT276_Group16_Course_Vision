package cmpt276.group16.models;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.*;

// The composite primary key of course_designations: a course id plus a designation.
// A separate class is needed because an entity can only have one id field
@Embeddable 
public class CourseDesignationId implements Serializable {
    
    // JPA requires composite id classes to be Serializable
    private static final long serialVersionUID = 1L;

    // Must be named courseId to match @MapsId("courseId") in CourseDesignation
    @Column(name = "course_id")
    private Long courseId;

    // Store as string
    @Enumerated(EnumType.STRING)
    private Designation designation;

    // For Hibernate only
    protected CourseDesignationId() {}

    // Used to look up or delete one designation, for example findById(new CourseDesignationId(id, Designation.W))
    public CourseDesignationId(Long courseId, Designation designation) {
        this.courseId = courseId;
        this.designation = designation;
    }

    // No setters: an id never changes once created
    public Long getCourseId() {
        return courseId;
    }

    public Designation getDesignation() {
        return designation;
    }

    // Two ids are the same when both the course and the designation match;
    // Hibernate relies on this to match rows
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof CourseDesignationId other)) {
            return false;
        }
        return (Objects.equals(other.getCourseId(), this.courseId) && other.getDesignation() == this.designation);
    }

    @Override
    public int hashCode() {
    return Objects.hash(courseId, designation);
    }
}
