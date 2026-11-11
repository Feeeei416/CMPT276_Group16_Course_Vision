package cmpt276.group16.models;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

import jakarta.persistence.*;

// One weekly meeting of a section on one day, for example Monday 10:30 to 11:20 at Burnaby.
// The importer splits the API's "Mo,We,Fr" into one meeting per day,
// so a time conflict check only compares meetings on the same day.
// Embeddable: a meeting has no id and is saved and deleted with its section
@Embeddable 
public class SectionMeeting {
    
    // Stored as text ("MONDAY") so the column still reads correctly if the enum order ever changes
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    // for example "Burnaby" or "Surrey"; null when the API doesn't give one
    private String campus;

    protected SectionMeeting() {}

    // Meetings without a day or times (for example online or TBA sections) are not saved at all,
    // so every saved meeting has a day and a time range
    public SectionMeeting(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String campus) {
        if (dayOfWeek == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("A meeting needs a day, a start time and an end time");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("A meeting must end after it starts");
        }
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.campus = campus;
    }

    // No setters: to change a meeting, replace the section's meetings with Section.replaceMeetings

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }
 
    public LocalTime getStartTime() {
        return startTime;
    }
 
    public LocalTime getEndTime() {
        return endTime;
    }
 
    public String getCampus() {
        return campus;
    }

    // A meeting has no id, so two meetings are the same when all their values are the same.
    // This lets tests and the importer compare meeting lists directly
        @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof SectionMeeting other)) {
            return false;
        }
        return (other.getDayOfWeek() == this.dayOfWeek
                && Objects.equals(other.getStartTime(), this.startTime)
                && Objects.equals(other.getEndTime(), this.endTime)
                && Objects.equals(other.getCampus(), this.campus));
    }
 
    @Override
    public int hashCode() {
        return Objects.hash(dayOfWeek, startTime, endTime, campus);
    }
 
    @Override
    public String toString() {
        String time = dayOfWeek + " " + startTime + "-" + endTime;
        if (campus == null) {
            return time;
        }
        return time + " " + campus;
    }
}
