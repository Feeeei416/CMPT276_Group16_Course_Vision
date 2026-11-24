package cmpt276.group16.models;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.*;

// One course data refresh from the SFU API, as listed in the log on the IT manager's Course refresh page.
// Every refresh gets a row, whether it worked or not and whether the app or an IT manager started it.
// The term is a year and season instead of a link to terms, because a refresh of a term SFU hasn't
// published yet stops before the term is added
@Entity
@Table(name = "course_refreshes")
public class CourseRefresh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int termYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Season season;

    // True when the app started it on its own, false when an IT manager pressed Refresh
    @Column(nullable = false)
    private boolean automatic;

    @Column(nullable = false)
    private Instant finishedAt;

    // What was saved. All 0 when the refresh failed, since nothing is saved then
    private int courses;
    private int sections;
    private int removedSections;
    private int notOffered;

    // What was skipped and why, one per line. Null when nothing was skipped
    @Column(columnDefinition = "TEXT")
    private String notes;

    // Why it failed, or null when it worked
    @Column(columnDefinition = "TEXT")
    private String error;

    protected CourseRefresh() {}

    public CourseRefresh(int termYear, Season season, boolean automatic, Instant finishedAt, int courses,
            int sections, int removedSections, int notOffered, List<String> notes, String error) {
        this.termYear = termYear;
        this.season = season;
        this.automatic = automatic;
        this.finishedAt = finishedAt;
        this.courses = courses;
        this.sections = sections;
        this.removedSections = removedSections;
        this.notOffered = notOffered;
        this.notes = notes.isEmpty() ? null : String.join("\n", notes);
        this.error = error;
    }

    // No setters: a refresh that has finished doesn't change
    public Long getId() {
        return id;
    }

    public int getTermYear() {
        return termYear;
    }

    public Season getSeason() {
        return season;
    }

    // for example "2027 Spring"
    public String getTermName() {
        return termYear + " " + season.getShowName();
    }

    public boolean isAutomatic() {
        return automatic;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getCourses() {
        return courses;
    }

    public int getSections() {
        return sections;
    }

    public int getRemovedSections() {
        return removedSections;
    }

    public int getNotOffered() {
        return notOffered;
    }

    public List<String> getNotes() {
        return notes == null ? List.of() : Arrays.asList(notes.split("\n"));
    }

    public String getError() {
        return error;
    }

    public boolean isFailed() {
        return error != null;
    }
}
