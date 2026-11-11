package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class RequisiteRuleRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private RequisiteRuleRepository requisiteRuleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Course saveCourse(String subject, String number) {
        return courseRepository.saveAndFlush(new Course(subject, number, "Some Course",
                new BigDecimal("3.00"), null, null, null));
    }

    private RequisiteRule saveRule(Course course, RequisiteRule parent, RuleKind kind, NodeType type) {
        return requisiteRuleRepository.saveAndFlush(new RequisiteRule(course, parent, kind, type));
    }

    // A top-level group has no parent and is found as the root of the course's tree
    @Test
    void rootGroupIsSavedAndFoundWithoutParent() {
        Course cmpt225 = saveCourse("CMPT", "225");
        RequisiteRule root = saveRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.AND);
        entityManager.clear();

        RequisiteRule found = requisiteRuleRepository
            .findByCourseIdAndRuleKindAndParentIsNull(cmpt225.getId(), RuleKind.PREREQUISITE).orElseThrow();
        assertThat(found.getId()).isEqualTo(root.getId());
        assertThat(found.getParent()).isNull();
        assertThat(found.getCourse().getNumber()).isEqualTo("225");
        assertThat(found.getNodeType()).isEqualTo(NodeType.AND);
        assertThat(found.isGroup()).isTrue();
    }

    // A course with no rules of that kind has no root
    @Test
    void findRootReturnsEmptyWhenCourseHasNoRules() {
        Course cmpt120 = saveCourse("CMPT", "120");

        assertThat(requisiteRuleRepository
            .findByCourseIdAndRuleKindAndParentIsNull(cmpt120.getId(), RuleKind.PREREQUISITE)).isEmpty();
    }

    // A course node keeps the required course, its code and the minimum grade
    @Test
    void courseNodeKeepsRequiredCourseAndMinGrade() {
        Course cmpt225 = saveCourse("CMPT", "225");
        Course cmpt125 = saveCourse("CMPT", "125");
        RequisiteRule root = saveRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.AND);

        RequisiteRule node = new RequisiteRule(cmpt225, root, RuleKind.PREREQUISITE, NodeType.COURSE);
        node.setRequiredSubjectAndNumber("CMPT", "125");
        node.setRequiredCourse(cmpt125);
        node.setMinGrade("C-");
        RequisiteRule saved = requisiteRuleRepository.saveAndFlush(node);
        entityManager.clear();

        RequisiteRule found = requisiteRuleRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getParent().getId()).isEqualTo(root.getId());
        assertThat(found.getReqSubject()).isEqualTo("CMPT");
        assertThat(found.getReqNumber()).isEqualTo("125");
        assertThat(found.getRequiredCourse().getId()).isEqualTo(cmpt125.getId());
        assertThat(found.getMinGrade()).isEqualTo("C-");
        assertThat(found.isGroup()).isFalse();
    }

    // The required course code is stored uppercase, like courses.subject and courses.course_number
    @Test
    void requiredCodeIsStoredUppercase() {
        Course cmpt225 = saveCourse("CMPT", "225");
        RequisiteRule node = new RequisiteRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.COURSE);
        node.setRequiredSubjectAndNumber(" cmpt ", "105w");

        assertThat(node.getReqSubject()).isEqualTo("CMPT");
        assertThat(node.getReqNumber()).isEqualTo("105W");
    }

    // A rule can name a course that isn't in the courses table yet: the code is kept and the link stays empty
    @Test
    void courseNodeCanNameCourseNotFetchedYet() {
        Course cmpt225 = saveCourse("CMPT", "225");
        RequisiteRule node = new RequisiteRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.COURSE);
        node.setRequiredSubjectAndNumber("MATH", "151");
        RequisiteRule saved = requisiteRuleRepository.saveAndFlush(node);
        entityManager.clear();

        RequisiteRule found = requisiteRuleRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getReqSubject()).isEqualTo("MATH");
        assertThat(found.getReqNumber()).isEqualTo("151");
        assertThat(found.getRequiredCourse()).isNull();
    }

    // A special node keeps free text, even when it is longer than 255 characters
    @Test
    void specialNodeKeepsLongText() {
        Course cmpt225 = saveCourse("CMPT", "225");
        String longText = "Permission of the instructor. ".repeat(20);
        RequisiteRule node = new RequisiteRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.SPECIAL);
        node.setSpecialText(longText);
        RequisiteRule saved = requisiteRuleRepository.saveAndFlush(node);
        entityManager.clear();

        RequisiteRule found = requisiteRuleRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSpecialText()).isEqualTo(longText);
        assertThat(found.getRequiredCourse()).isNull();
    }

    // A group's children come back in the order they were saved, and only that group's children
    @Test
    void findsChildrenOfOneGroupInOrder() {
        Course cmpt225 = saveCourse("CMPT", "225");
        RequisiteRule root = saveRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.AND);
        RequisiteRule first = saveRule(cmpt225, root, RuleKind.PREREQUISITE, NodeType.COURSE);
        RequisiteRule orGroup = saveRule(cmpt225, root, RuleKind.PREREQUISITE, NodeType.OR);
        RequisiteRule grandchild = saveRule(cmpt225, orGroup, RuleKind.PREREQUISITE, NodeType.SPECIAL);
        entityManager.clear();

        assertThat(requisiteRuleRepository.findByParentIdOrderByIdAsc(root.getId()))
            .extracting(RequisiteRule::getId)
            .containsExactly(first.getId(), orGroup.getId());
        assertThat(requisiteRuleRepository.findByParentIdOrderByIdAsc(orGroup.getId()))
            .extracting(RequisiteRule::getId)
            .containsExactly(grandchild.getId());
    }

    // Loading a whole tree returns only that course's rules of that kind, not its coreqs or other courses' rules
    @Test
    void findsWholeTreeOfOneKindForOneCourse() {
        Course cmpt225 = saveCourse("CMPT", "225");
        Course cmpt276 = saveCourse("CMPT", "276");
        RequisiteRule root = saveRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.AND);
        RequisiteRule child = saveRule(cmpt225, root, RuleKind.PREREQUISITE, NodeType.COURSE);
        saveRule(cmpt225, null, RuleKind.COREQUISITE, NodeType.COURSE);
        saveRule(cmpt276, null, RuleKind.PREREQUISITE, NodeType.COURSE);
        entityManager.clear();

        assertThat(requisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc(cmpt225.getId(), RuleKind.PREREQUISITE))
            .extracting(RequisiteRule::getId)
            .containsExactly(root.getId(), child.getId());
    }

    // Finds every rule that requires a given course, for example to see what CMPT 125 unlocks
    @Test
    void findsRulesThatRequireACourse() {
        Course cmpt125 = saveCourse("CMPT", "125");
        Course cmpt225 = saveCourse("CMPT", "225");
        Course cmpt295 = saveCourse("CMPT", "295");
        RequisiteRule for225 = new RequisiteRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.COURSE);
        for225.setRequiredCourse(cmpt125);
        requisiteRuleRepository.saveAndFlush(for225);
        RequisiteRule for295 = new RequisiteRule(cmpt295, null, RuleKind.PREREQUISITE, NodeType.COURSE);
        for295.setRequiredCourse(cmpt125);
        requisiteRuleRepository.saveAndFlush(for295);
        entityManager.clear();

        assertThat(requisiteRuleRepository.findByRequiredCourseId(cmpt125.getId()))
            .extracting(rule -> rule.getCourse().getNumber())
            .containsExactlyInAnyOrder("225", "295");
    }

    // A rule must belong to a course
    @Test
    void rejectsMissingCourse() {
        assertThatThrownBy(() -> saveRule(null, null, RuleKind.PREREQUISITE, NodeType.AND))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // A rule must say whether it is a prerequisite or a corequisite
    @Test
    void rejectsMissingRuleKind() {
        Course cmpt225 = saveCourse("CMPT", "225");

        assertThatThrownBy(() -> saveRule(cmpt225, null, null, NodeType.AND))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // A rule must say what kind of node it is
    @Test
    void rejectsMissingNodeType() {
        Course cmpt225 = saveCourse("CMPT", "225");

        assertThatThrownBy(() -> saveRule(cmpt225, null, RuleKind.PREREQUISITE, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Only AND and OR groups can have children, so a course or special node can't be a parent
    @Test
    void rejectsLeafAsParent() {
        Course cmpt225 = saveCourse("CMPT", "225");
        RequisiteRule leaf = saveRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.COURSE);

        assertThatThrownBy(() -> new RequisiteRule(cmpt225, leaf, RuleKind.PREREQUISITE, NodeType.COURSE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // A child must belong to the same course as its parent
    @Test
    void rejectsParentFromAnotherCourse() {
        Course cmpt225 = saveCourse("CMPT", "225");
        Course cmpt276 = saveCourse("CMPT", "276");
        RequisiteRule otherRoot = saveRule(cmpt276, null, RuleKind.PREREQUISITE, NodeType.AND);

        assertThatThrownBy(() -> new RequisiteRule(cmpt225, otherRoot, RuleKind.PREREQUISITE, NodeType.COURSE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // A prerequisite tree can't contain corequisite rules, and the other way round
    @Test
    void rejectsParentWithDifferentRuleKind() {
        Course cmpt225 = saveCourse("CMPT", "225");
        RequisiteRule prereqRoot = saveRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.AND);

        assertThatThrownBy(() -> new RequisiteRule(cmpt225, prereqRoot, RuleKind.COREQUISITE, NodeType.COURSE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // A unit node keeps how many units are needed
    @Test
    void unitNodeKeepsMinUnits() {
        Course cmpt300 = saveCourse("CMPT", "300");
        RequisiteRule rule = new RequisiteRule(cmpt300, null, RuleKind.PREREQUISITE, NodeType.MIN_UNITS);
        rule.setMinUnits(new BigDecimal("60"));
        RequisiteRule saved = requisiteRuleRepository.saveAndFlush(rule);
        entityManager.clear();

        RequisiteRule found = requisiteRuleRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getNodeType()).isEqualTo(NodeType.MIN_UNITS);
        assertThat(found.getMinUnits()).isEqualByComparingTo("60");
        assertThat(found.isGroup()).isFalse();
    }

    // Rules are imported unless they are created as entered by hand
    @Test
    void sourceIsSaved() {
        Course cmpt225 = saveCourse("CMPT", "225");
        RequisiteRule imported = saveRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.AND);
        RequisiteRule manual = requisiteRuleRepository.saveAndFlush(new RequisiteRule(
                cmpt225, null, RuleKind.COREQUISITE, NodeType.SPECIAL, RequisiteRule.Source.MANUAL));
        entityManager.clear();

        assertThat(requisiteRuleRepository.findById(imported.getId()).orElseThrow().getSource())
            .isEqualTo(RequisiteRule.Source.IMPORTED);
        assertThat(requisiteRuleRepository.findById(manual.getId()).orElseThrow().getSource())
            .isEqualTo(RequisiteRule.Source.MANUAL);
    }

    // A hand-entered node can't sit in an imported tree
    @Test
    void rejectsParentWithDifferentSource() {
        Course cmpt225 = saveCourse("CMPT", "225");
        RequisiteRule root = saveRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.AND);

        assertThatThrownBy(() -> new RequisiteRule(cmpt225, root, RuleKind.PREREQUISITE,
                NodeType.COURSE, RequisiteRule.Source.MANUAL))
            .isInstanceOf(IllegalArgumentException.class);
    }
}