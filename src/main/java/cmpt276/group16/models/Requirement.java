package cmpt276.group16.models;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;

// One rule in a requirement set, for example "1 of MATH 150, 151, 154, 157" or "6 units of W".
// Which fields are used depends on the kind:
//   COURSE       its one course in requirement_courses
//   CHOOSE       courseCount, and the courses to choose from
//   UNITS_FROM   units, the listed courses and/or subject + minNumber, excluded courses,
//                and optionally maxCoursesBelow + belowNumber ("at most two 300-level courses")
//   AREAS        courseCount, and the courses with their area; required courses must be among them
//   DESIGNATION  units, and the designations that count
//   TOTAL_UNITS  units, and minNumber for upper division only
//   NOTE         text
@Entity
@Table(name = "requirements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"requirement_set_id", "position"}))
public class Requirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many requirements belong to one set
    @ManyToOne
    @JoinColumn(name = "requirement_set_id", nullable = false)
    private RequirementSet requirementSet;

    // The order the requirements are shown in, the same order as the Calendar
    @Column(nullable = false)
    private int position;

    // The Calendar heading it sits under, for example "Lower division" or "Breadth"
    @Column(nullable = false)
    private String category;

    // A short name for a CHOOSE or UNITS_FROM rule, for example "Calculus I"; null when the courses say enough
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequirementKind kind;

    // CHOOSE and AREAS: how many courses, or how many areas
    private Integer courseCount;

    // UNITS_FROM, DESIGNATION and TOTAL_UNITS: how many units
    @Column(precision = 5, scale = 2)
    private BigDecimal units;

    // UNITS_FROM: the subject of a range, for example CMPT in "CMPT 400 and above"
    private String subject;

    // UNITS_FROM: the lowest course number of the range. TOTAL_UNITS: 300 to count upper division only
    private Integer minNumber;

    // UNITS_FROM: at most this many courses numbered below belowNumber, for example 2 courses below 400
    private Integer maxCoursesBelow;

    private Integer belowNumber;

    // DESIGNATION: a course with any of these counts, so B-Hum or B-Soc is stored as both
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "requirement_designations", joinColumns = @JoinColumn(name = "requirement_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "designation", nullable = false)
    private Set<Designation> designations = EnumSet.noneOf(Designation.class);

    // The lowest grade that counts, for example "C-" for WQB; null if any passing grade counts
    private String minGrade;

    // True for rules a course can only count toward one of, like Breadth, Depth and the BSc tables,
    // which each say "additional". Required courses and WQB can overlap with anything
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean exclusive;

    // NOTE: the rule as the student reads it
    @Column(columnDefinition = "TEXT")
    private String text;

    protected Requirement() {}

    public Requirement(RequirementSet requirementSet, int position, String category, RequirementKind kind) {
        if (position < 0) {
            throw new IllegalArgumentException("Position can't be negative");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("A requirement needs a category");
        }
        this.requirementSet = requirementSet;
        this.position = position;
        this.category = category.trim();
        this.kind = kind;
    }

    public Long getId() {
        return id;
    }

    // No setters for the set and kind: changing them would make it a different rule
    public RequirementSet getRequirementSet() {
        return requirementSet;
    }

    public RequirementKind getKind() {
        return kind;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getCourseCount() {
        return courseCount;
    }

    public void setCourseCount(Integer courseCount) {
        this.courseCount = courseCount;
    }

    public BigDecimal getUnits() {
        return units;
    }

    public void setUnits(BigDecimal units) {
        this.units = units;
    }

    public String getSubject() {
        return subject;
    }

    // Stored uppercase so it matches courses.subject
    public void setSubject(String subject) {
        this.subject = subject == null ? null : Course.normalizeSubjectOrNumber(subject);
    }

    public Integer getMinNumber() {
        return minNumber;
    }

    public void setMinNumber(Integer minNumber) {
        this.minNumber = minNumber;
    }

    public Integer getMaxCoursesBelow() {
        return maxCoursesBelow;
    }

    public Integer getBelowNumber() {
        return belowNumber;
    }

    // Set together, since one means nothing without the other
    public void setLimitBelow(Integer maxCoursesBelow, Integer belowNumber) {
        this.maxCoursesBelow = maxCoursesBelow;
        this.belowNumber = belowNumber;
    }

    public Set<Designation> getDesignations() {
        return designations;
    }

    public void setDesignations(Set<Designation> designations) {
        this.designations = designations.isEmpty() ? EnumSet.noneOf(Designation.class) : EnumSet.copyOf(designations);
    }

    public String getMinGrade() {
        return minGrade;
    }

    public void setMinGrade(String minGrade) {
        this.minGrade = minGrade;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
