package cmpt276.group16.models;

import java.time.Instant;
import java.util.*;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;

// One section of a course offering.
// For example CMPT 225 D100 (LEC) or D101 (TUT) in 2027 Spring
@Entity 
@Table(name = "sections",
       uniqueConstraints = @UniqueConstraint(columnNames = {"offering_id", "section_code"}))
public class Section {
    
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many sections belong to one offering (one course in one term)
    @ManyToOne
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    // for example "D100", always stored uppercase
    @Column(nullable = false)
    private String sectionCode;

    // Store as string
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SectionType sectionType;

    // True for the section a student registers in on goSFU, usually the lecture
    // (classType "e", "enrollment section", in the API).
    // False for sections attached to it, such as most tutorials and labs (classType "n")
    @Column(nullable = false)
    private boolean primarySection;

    // Which group the section belongs to, as the API gives it, for example "1".
    // Every section has one: a lecture and its labs share the same value within an offering,
    // so a schedule pairs them by matching it. Some, like open labs, match no lecture
    private String associatedClass;

    // Empty when the section has no times, for example online or TBA.
    // OrderColumn keeps the order the importer added them in (Monday first)
    @ElementCollection
    @CollectionTable(name = "section_meetings", joinColumns = @JoinColumn(name = "section_id"))
    @OrderColumn(name = "meeting_order")
    private List<SectionMeeting> meetings = new ArrayList<>();

    // False when SFU stopped listing the section after it was saved, for example a cancelled tutorial.
    // The row is kept instead of deleted, since a student's schedule can point at it.
    // The default fills in rows saved before this column existed, which were all listed
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean offered = true;

    // When this section was last fetched from the API
    @Column(nullable = false)
    private Instant fetchedAt;

    protected Section() {}

    public Section(CourseOffering offering, String sectionCode, SectionType sectionType,
            boolean primarySection, String associatedClass) {
        this.offering = offering;
        // Same rule as course subjects and numbers: trimmed and uppercase, so "d100" becomes "D100"
        this.sectionCode = Course.normalizeSubjectOrNumber(sectionCode);
        this.sectionType = sectionType;
        this.primarySection = primarySection;
        this.associatedClass = associatedClass;
        // A new section counts as just fetched
        this.fetchedAt = Instant.now();
    }

    // Call this every time the section is fetched from the API, even if nothing changed,
    // so fetchedAt always shows when the data was last checked
    public void markFetched() {
        this.fetchedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    // No setters for offering and sectionCode:
    // they identify the section, so changing them would make it a different section
    public CourseOffering getOffering() {
        return offering;
    }

    public String getSectionCode() {
        return sectionCode;
    }

    public SectionType getSectionType() {
        return sectionType;
    }
 
    public void setSectionType(SectionType sectionType) {
        this.sectionType = sectionType;
    }
    public boolean isPrimarySection() {
        return primarySection;
    }
 
    public void setPrimarySection(boolean primarySection) {
        this.primarySection = primarySection;
    }

    public String getAssociatedClass() {
        return associatedClass;
    }
 
    public void setAssociatedClass(String associatedClass) {
        this.associatedClass = associatedClass;
    }

    public List<SectionMeeting> getMeetings() {
        return Collections.unmodifiableList(meetings);
    }

    // Replaces all meetings with the ones from the latest fetch.
    // Clears and refills the same list instead of assigning a new one,
    // because Hibernate tracks changes through the list it loaded
    public void replaceMeetings(List<SectionMeeting> newMeetings) {
        meetings.clear();
        meetings.addAll(newMeetings);
    }

    public boolean isOffered() {
        return offered;
    }

    // Lets a re-fetch update whether SFU still lists the section
    public void setOffered(boolean offered) {
        this.offered = offered;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
