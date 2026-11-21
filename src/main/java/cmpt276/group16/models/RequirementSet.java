package cmpt276.group16.models;

import jakarta.persistence.*;

// The graduation requirements for one program and entry year, for example Computing Science 2024.
// A student follows the Calendar of the year they entered, so each year has its own set
@Entity
@Table(name = "requirement_sets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"program", "entry_year"}))
public class RequirementSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Program program;

    // Matches StudentProfile.cohortYear
    @Column(name = "entry_year", nullable = false)
    private int entryYear;

    // The Calendar page the requirements were taken from, so students and advisors can check them
    @Column(length = 500)
    private String calendarUrl;

    protected RequirementSet() {}

    public RequirementSet(Program program, int entryYear, String calendarUrl) {
        this.program = program;
        this.entryYear = entryYear;
        this.calendarUrl = calendarUrl;
    }

    public Long getId() {
        return id;
    }

    // No setters for program and entryYear: they say which students the set is for
    public Program getProgram() {
        return program;
    }

    public int getEntryYear() {
        return entryYear;
    }

    public String getCalendarUrl() {
        return calendarUrl;
    }

    public void setCalendarUrl(String calendarUrl) {
        this.calendarUrl = calendarUrl;
    }

    // For example "Computing Science, entry 2024"
    public String getShowName() {
        return program.getShowName() + ", entry " + entryYear;
    }
}
