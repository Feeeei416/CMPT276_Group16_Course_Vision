package cmpt276.group16.models;

import jakarta.persistence.*;

@Entity
@Table(name = "scheduled_courses", uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_id", "offering_id"}))
public class ScheduledCourse {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many scheduled courses belong to one schedule
    @ManyToOne
    @JoinColumn(name = "schedule_id", nullable = false)
    private NextSemesterSchedule schedule;
    
    // Many schedules courses point to one course offering
    @ManyToOne 
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    // The lecture section you enroll in
    // Many students can pick the same one
    @ManyToOne 
    @JoinColumn(name = "primary_section_id", nullable = false)
    private Section primarySection;

    // The lab or tutorial goes with the lecture
    // Empty when the courses have none
    @ManyToOne 
    @JoinColumn(name = "attached_section_id")
    private Section attachedSection;

    protected ScheduledCourse() {}

    public ScheduledCourse(NextSemesterSchedule schedule, CourseOffering offering, Section primarySection, Section attachedSection) {
        this.schedule = schedule;
        this.offering = offering;
        this.primarySection = primarySection;
        this.attachedSection = attachedSection;
    }

    public Long getId() {
        return id;
    }

    public NextSemesterSchedule getSchedule() {
        return schedule;
    }

    public CourseOffering getOffering() {
        return offering;
    }

    public Section getPrimarySection() {
        return primarySection;
    }

    public Section getAttachedSection() {
        return attachedSection;
    }
}
