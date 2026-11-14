package cmpt276.group16.requisites;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.NodeType;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.RequisiteRuleRepository;
import cmpt276.group16.models.RuleKind;
import cmpt276.group16.requisites.Requirement.And;
import cmpt276.group16.requisites.Requirement.CourseReq;
import cmpt276.group16.requisites.Requirement.MinUnits;
import cmpt276.group16.requisites.Requirement.Or;
import cmpt276.group16.requisites.Requirement.Special;

// @DataJpaTest will only set up repos, so the service is added with @Import.
// The manual rules come from a test file with one rule, for MATH 260, so these tests don't change with the real one
@DataJpaTest
@Import({RequisiteImportService.class, ManualRules.class})
@TestPropertySource(properties = "app.manual-rules=classpath:requisites/test-manual-rules.json")
class RequisiteImportServiceTest {

    private static final String CMPT_225 = "(MACM 101 and (CMPT 125, CMPT 129 or CMPT 135)) or (ENSC 251 and ENSC 252), "
            + "all with a minimum grade of C-.";

    @Autowired
    private RequisiteImportService requisiteImportService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private RequisiteRuleRepository requisiteRuleRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Course saveCourse(String subject, String number, String prereqText) {
        return courseRepository.saveAndFlush(new Course(subject, number, "Some Course",
                new BigDecimal("3.00"), null, prereqText, null));
    }

    // Imports, then reloads the course so the checks below read what was saved
    private Course importAndReload(Course course) {
        requisiteImportService.importPrerequisites(course);
        entityManager.flush();
        entityManager.clear();
        return courseRepository.findById(course.getId()).orElseThrow();
    }

    // Builds the saved rows back into a Requirement, to compare with what the parser tests print.
    // "" when there are no rows; several roots are joined with " | "
    private String stored(Course course) {
        List<RequisiteRule> rows = requisiteRuleRepository
                .findByCourseIdAndRuleKindOrderByIdAsc(course.getId(), RuleKind.PREREQUISITE);
        return rows.stream()
                .filter(row -> row.getParent() == null)
                .map(root -> rebuild(root, rows).toString())
                .collect(Collectors.joining(" | "));
    }

    private static Requirement rebuild(RequisiteRule node, List<RequisiteRule> rows) {
        List<Requirement> children = rows.stream()
                .filter(row -> row.getParent() != null && row.getParent().getId().equals(node.getId()))
                .map(child -> rebuild(child, rows))
                .toList();
        return switch (node.getNodeType()) {
            case AND -> new And(children);
            case OR -> new Or(children);
            case COURSE -> new CourseReq(node.getReqSubject(), node.getReqNumber(), node.getMinGrade(),
                    node.isConcurrentOk());
            case MIN_UNITS -> new MinUnits(node.getMinUnits());
            case SPECIAL -> new Special(node.getSpecialText());
        };
    }

    private List<RequisiteRule> importedRules(Course course) {
        return requisiteRuleRepository.findByCourseIdAndRuleKindAndSourceOrderByIdDesc(
                course.getId(), RuleKind.PREREQUISITE, RequisiteRule.Source.IMPORTED);
    }

    @Test
    void savesTheParsedTree() {
        Course cmpt225 = importAndReload(saveCourse("CMPT", "225", CMPT_225));

        assertThat(cmpt225.getParseStatus()).isEqualTo(Course.ParseStatus.PARSED);
        assertThat(stored(cmpt225)).isEqualTo("OR(AND(MACM 101 (C-), OR(CMPT 125 (C-), CMPT 129 (C-), CMPT 135 (C-))), "
                + "AND(ENSC 251 (C-), ENSC 252 (C-)))");
    }

    @Test
    void savesUnitsSpecialAndConcurrent() {
        Course course = importAndReload(saveCourse("ENSC", "999",
                "(MATH 260 and 60 units) or permission of the instructor. MATH 260 may be taken concurrently."));

        assertThat(stored(course))
                .isEqualTo("OR(AND(MATH 260 (concurrent), 60 units), SPECIAL(permission of the instructor))");
    }

    // A required course that's already imported is linked; one that isn't waits with just its code
    @Test
    void linksRequiredCoursesThatExist() {
        Course macm101 = saveCourse("MACM", "101", null);
        Course cmpt225 = importAndReload(saveCourse("CMPT", "225", CMPT_225));

        List<RequisiteRule> courseNodes = requisiteRuleRepository
                .findByCourseIdAndRuleKindOrderByIdAsc(cmpt225.getId(), RuleKind.PREREQUISITE).stream()
                .filter(row -> row.getNodeType() == NodeType.COURSE)
                .toList();
        assertThat(courseNodes).filteredOn(row -> row.getReqNumber().equals("101"))
                .singleElement()
                .satisfies(row -> assertThat(row.getRequiredCourse().getId()).isEqualTo(macm101.getId()));
        assertThat(courseNodes).filteredOn(row -> !row.getReqNumber().equals("101"))
                .allSatisfy(row -> assertThat(row.getRequiredCourse()).isNull());
    }

    // ENSC 405W's text. The course is flagged and prereqText is kept to show the student
    @Test
    void unparseableTextIsFlaggedAndKept() {
        String text = "(ENSC 105W or MSE 101W), ENSC 204, all with a minimum grade of C-, completion of a minimum of "
                + "22 units of required upper division ENSC courses.";
        Course ensc405w = importAndReload(saveCourse("ENSC", "405W", text));

        assertThat(ensc405w.getParseStatus()).isEqualTo(Course.ParseStatus.FAILED);
        assertThat(ensc405w.getPrereqText()).isEqualTo(text);
        assertThat(stored(ensc405w)).isEmpty();
    }

    // ECON 103's text is ""
    @Test
    void noPrerequisitesSavesNothing() {
        Course econ103 = importAndReload(saveCourse("ECON", "103", ""));

        assertThat(econ103.getParseStatus()).isEqualTo(Course.ParseStatus.PARSED);
        assertThat(stored(econ103)).isEmpty();
    }

    @Test
    void importingAgainReplacesInsteadOfAdding() {
        Course cmpt225 = importAndReload(saveCourse("CMPT", "225", CMPT_225));
        int rowsAfterFirst = importedRules(cmpt225).size();

        cmpt225 = importAndReload(cmpt225);

        assertThat(importedRules(cmpt225)).hasSize(rowsAfterFirst);
    }

    @Test
    void changedTextReplacesTheOldTree() {
        Course course = importAndReload(saveCourse("CMPT", "225", CMPT_225));

        course.setPrereqText("CMPT 125 or CMPT 135.");
        course = importAndReload(course);

        assertThat(stored(course)).isEqualTo("OR(CMPT 125, CMPT 135)");
    }

    // Otherwise the old rule would stay for text that no longer says it
    @Test
    void textThatStopsParsingRemovesTheOldTree() {
        Course course = importAndReload(saveCourse("CMPT", "225", CMPT_225));

        course.setPrereqText("Something new the parser can't read.");
        course = importAndReload(course);

        assertThat(course.getParseStatus()).isEqualTo(Course.ParseStatus.FAILED);
        assertThat(stored(course)).isEmpty();
    }

    // Importing again doesn't erase rules entered by hand
    @Test
    void rulesEnteredByHandAreKeptAndWin() {
        Course cmpt225 = saveCourse("CMPT", "225", CMPT_225);
        RequisiteRule manual = new RequisiteRule(cmpt225, null, RuleKind.PREREQUISITE, NodeType.COURSE,
                RequisiteRule.Source.MANUAL);
        manual.setRequiredSubjectAndNumber("CMPT", "125");
        manual.setMinGrade("B");
        requisiteRuleRepository.saveAndFlush(manual);

        cmpt225 = importAndReload(cmpt225);

        assertThat(cmpt225.getParseStatus()).isEqualTo(Course.ParseStatus.PARSED);
        assertThat(stored(cmpt225)).isEqualTo("CMPT 125 (B)");
        assertThat(importedRules(cmpt225)).isEmpty();
    }

    // CMPT 276's text doesn't parse because of its "may be substituted" sentence. With a manual rule the student
    // still has rules to go by, so the course isn't flagged
    @Test
    void rulesEnteredByHandMeanTheCourseIsNotFlagged() {
        Course cmpt276 = saveCourse("CMPT", "276", "CMPT 225, with a minimum grade of C-. "
                + "MATH 154 or MATH 157 with at least a B+ may be substituted for MATH 151 or MATH 150.");
        RequisiteRule manual = new RequisiteRule(cmpt276, null, RuleKind.PREREQUISITE, NodeType.COURSE,
                RequisiteRule.Source.MANUAL);
        manual.setRequiredSubjectAndNumber("CMPT", "225");
        manual.setMinGrade("C-");
        requisiteRuleRepository.saveAndFlush(manual);

        cmpt276 = importAndReload(cmpt276);

        assertThat(cmpt276.getParseStatus()).isEqualTo(Course.ParseStatus.PARSED);
        assertThat(stored(cmpt276)).isEqualTo("CMPT 225 (C-)");
    }

    private static final String MATH_260 = "MATH 152 with a minimum grade of C-; or MATH 155 or 158, with a grade "
            + "of at least B; MATH 232 or 240, with a minimum grade of C-.";

    // MATH 260's text doesn't parse, so its rule comes from the manual rules file
    @Test
    void ruleFromTheManualRulesFileIsSaved() {
        Course math260 = importAndReload(saveCourse("MATH", "260", MATH_260));

        assertThat(math260.getParseStatus()).isEqualTo(Course.ParseStatus.PARSED);
        assertThat(stored(math260))
                .isEqualTo("AND(OR(MATH 152 (C-), MATH 155 (B), MATH 158 (B)), OR(MATH 232 (C-), MATH 240 (C-)))");
        assertThat(requisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc(math260.getId(), RuleKind.PREREQUISITE))
                .allSatisfy(row -> assertThat(row.getSource()).isEqualTo(RequisiteRule.Source.MANUAL));
    }

    // Saved again on each import, so a fix to the file replaces the old rule instead of adding to it
    @Test
    void importingAgainReplacesTheManualRule() {
        Course math260 = importAndReload(saveCourse("MATH", "260", MATH_260));
        int rowsAfterFirst = requisiteRuleRepository
                .findByCourseIdAndRuleKindOrderByIdAsc(math260.getId(), RuleKind.PREREQUISITE).size();

        math260 = importAndReload(math260);

        assertThat(requisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc(math260.getId(), RuleKind.PREREQUISITE))
                .hasSize(rowsAfterFirst);
        assertThat(importedRules(math260)).isEmpty();
    }
}
