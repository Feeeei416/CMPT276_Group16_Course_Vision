package cmpt276.group16.models;

import java.util.Objects;

import jakarta.persistence.*;

// One node in a course's requisite tree. For example, CMPT 225's prerequisites are stored as:
//   AND
//   ├── COURSE  CMPT 125, min grade C-
//   └── OR
//       ├── COURSE  MACM 101
//       └── SPECIAL "permission of the instructor"
// Each box is one row, and a child points at its group through parent
@Entity
@Table(name = "requisite_rules")
public class RequisiteRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The course this rule belongs to, for example CMPT 225
    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    // The AND or OR group this node sits in; null for the root of the tree
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private RequisiteRule parent;

    // Whether this rule is a prerequisite or a corequisite
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleKind ruleKind;

    // AND or OR for a group, COURSE or SPECIAL for a leaf
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeType;

    // Only for course nodes: the code of the required course, for example "CMPT" + "125", always uppercase.
    // Kept even when requiredCourse is set, because the required course may not be fetched yet
    private String reqSubject;

    private String reqNumber;

    // Only for course nodes: the required course, once it is in the courses table; null until then
    @ManyToOne
    @JoinColumn(name = "required_course_id")
    private Course requiredCourse;

    // Only for course nodes: the lowest grade that counts, for example "C-"; null if any passing grade counts
    private String minGrade;

    // Only for special nodes: requirement text that can't be turned into courses,
    // for example "permission of the instructor"
    @Column(columnDefinition = "TEXT")
    private String specialText;

    protected RequisiteRule() {}

    // Pass null as parent for the root of the tree
    public RequisiteRule(Course course, RequisiteRule parent, RuleKind ruleKind, NodeType nodeType) {
        // A child must fit into its parent's tree
        if (parent != null) {
            if (!parent.isGroup()) {
                throw new IllegalArgumentException("Only AND and OR groups can have children");
            }
            else if(!Objects.equals(parent.getCourse().getId(), course.getId())) {
                throw new IllegalArgumentException("Parent belongs to other course");
            }
            else if(parent.getRuleKind() != ruleKind) {
                throw new IllegalArgumentException("Parent has a different rule kind");
            }
        }
        this.course = course;
        this.parent = parent;
        this.ruleKind = ruleKind;
        this.nodeType = nodeType;
    }

    // True for AND and OR nodes, the only ones that can have children
    public boolean isGroup() {
        if (nodeType == NodeType.AND || nodeType == NodeType.OR){
            return true;
        }
        return false;
    }

    public Long getId() {
        return id;
    }

    // No setters for course, parent, ruleKind and nodeType: 
    // they decide where the node sits in the tree, so changing them would make it a different node
    public Course getCourse() {
        return course;
    }

    public RequisiteRule getParent() {
        return parent;
    }

    public RuleKind getRuleKind() {
        return ruleKind;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public String getReqSubject() {
        return reqSubject;
    }

    public String getReqNumber() {
        return reqNumber;
    }

    // Sets subject and number together, stored uppercase so they match courses.subject and courses.course_number
    public void setRequiredSubjectAndNumber(String subject, String number) {
        this.reqSubject = Course.normalizeSubjectOrNumber(subject);
        this.reqNumber = Course.normalizeSubjectOrNumber(number);
    }

    public Course getRequiredCourse() {
        return requiredCourse;
    }

    // Links the rule to the required course once that course is in the courses table
    public void setRequiredCourse(Course requiredCourse) {
        this.requiredCourse = requiredCourse;
    }
    
    public String getMinGrade() {
        return minGrade;
    }

    public void setMinGrade(String minGrade) {
        this.minGrade = minGrade;
    }

    public String getSpecialText() {
        return specialText;
    }

    public void setSpecialText(String specialText) {
        this.specialText = specialText;
    }
}
