package cmpt276.group16.models;

import jakarta.persistence.*;

// Which WQB requirements a course counts for, one row per course and designation.
// For example MACM 101 has two rows: Q and B_SCI
@Entity 
@Table(name = "course_designations")
public class CourseDesignation {

    // The course and the designation together are the primary key.
    @EmbeddedId
    private CourseDesignationId id;

    // @MapsId fills id.courseId from this course, so both share the course_id column
    @ManyToOne
    @MapsId("courseId")
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    protected CourseDesignation() {}

    // The course must already be saved, otherwise course.getId() is null and the id is incomplete
    public CourseDesignation(Course course, Designation designation) {
        this.id = new CourseDesignationId(course.getId(), designation);
        this.course = course;
    }

    // No setters: the course and designation are the primary key,
    // so to change one, delete this row and save a new one
    public CourseDesignationId getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    // Shortcut so callers don't have to go through the id
    public Designation getDesignation() {
        return id.getDesignation();
    }
}
