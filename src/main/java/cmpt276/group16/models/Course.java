package cmpt276.group16.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.*;

@Entity 
@Table (name = "courses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"subject", "course_number"}))
public class Course {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // for example "CMPT", always stored uppercase
    @Column(nullable = false)
    private String subject;

    // for example "225" or "105W", always stored uppercase
    @Column(name = "course_number", nullable = false)
    private String number;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal units;

    // TEXT because descriptions can be longer than the default 255 characters
    @Column(columnDefinition = "TEXT")
    private String description;

    // Null when the course has no prerequisites
    @Column(columnDefinition = "TEXT")
    private String prereqText;

    // Null when the course has no corequisites
    @Column(columnDefinition = "TEXT")
    private String coreqText;

    // When this course was last fetched from the API
    @Column(nullable = false)
    private Instant fetchedAt;

    protected Course() {}

    public Course(String subject, String number, String title, BigDecimal units, 
            String description, String prereqText, String coreqText) {
        this.subject = normalizeSubjectOrNumber(subject);
        this.number = normalizeSubjectOrNumber(number);
        this.title = title;
        this.units = units;
        this.description = description;
        this.prereqText = prereqText;
        this.coreqText = coreqText;
        // A new course counts as just fetched
        this.fetchedAt = Instant.now();
    }

    // Call this every time the course is fetched from the API, even if nothing changed,
    // so fetchedAt always shows when the data was last checked
    public void markFetched() {
        this.fetchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    // No setters for subject and number: 
    // they identify the course, so changing them would make it a different course
    public String getSubject() {
        return subject;
    }

    public String getNumber() {
        return number;
    }

    // The setters below let a re-fetch update the course details in place
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getUnits() {
        return units;
    }

    public void setUnits(BigDecimal units) {
        this.units = units;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrereqText() {
        return prereqText;
    }

    public void setPrereqText(String prereqText) {
        this.prereqText = prereqText;
    }

    public String getCoreqText() {
        return coreqText;
    }

    public void setCoreqText(String coreqText) {
        this.coreqText = coreqText;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    // Make subject and number case insensitive
    public static String normalizeSubjectOrNumber(String str) {
        return str.trim().toUpperCase(Locale.ROOT);
    }
}
