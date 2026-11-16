package cmpt276.group16.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.Grades;
import cmpt276.group16.models.NodeType;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.RuleKind;
import cmpt276.group16.models.TranscriptCourse;

// Plain unit tests: no Spring or database. Each test builds a small prerequisite tree by hand
class PrerequisiteCheckTest {

    // The course whose prerequisites are being checked
    private final Course target = new Course("CMPT", "276", "Intro Software Engineering",
            new BigDecimal("3.00"), null, null, null);

    // Every node made by the helpers below, in the order the repository would return them
    private final List<RequisiteRule> rules = new ArrayList<>();

    private RequisiteRule group(RequisiteRule parent, NodeType type) {
        RequisiteRule rule = new RequisiteRule(target, parent, RuleKind.PREREQUISITE, type);
        rules.add(rule);
        return rule;
    }

    private RequisiteRule requires(RequisiteRule parent, String subject, String number, String minGrade) {
        RequisiteRule rule = group(parent, NodeType.COURSE);
        rule.setRequiredSubjectAndNumber(subject, number);
        rule.setMinGrade(minGrade);
        return rule;
    }

    private RequisiteRule requiresUnits(RequisiteRule parent, String units) {
        RequisiteRule rule = group(parent, NodeType.MIN_UNITS);
        rule.setMinUnits(new BigDecimal(units));
        return rule;
    }

    private RequisiteRule special(RequisiteRule parent, String text) {
        RequisiteRule rule = group(parent, NodeType.SPECIAL);
        rule.setSpecialText(text);
        return rule;
    }

    // The student isn't needed to check prerequisites, so it's left null
    private static TranscriptCourse took(String term, String subject, String number, String grade) {
        return new TranscriptCourse(null, term, subject, number, "Title", new BigDecimal("3.00"), grade);
    }

    @Test
    void courseWithNoPrerequisitesIsMet() {
        PrerequisiteCheck.Result result = PrerequisiteCheck.check(List.of(), List.of());

        assertThat(result.met()).isTrue();
        assertThat(result.missing()).isEmpty();
        assertThat(result.toConfirm()).isEmpty();
    }

    @Test
    void passedPrerequisiteIsMet() {
        requires(null, "CMPT", "225", "C-");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules,
                List.of(took("2025 Fall", "CMPT", "225", "B")));

        assertThat(result.met()).isTrue();
        assertThat(result.missing()).isEmpty();
    }

    // Exactly the minimum grade is enough
    @Test
    void minimumGradeItselfIsMet() {
        requires(null, "CMPT", "225", "C-");

        assertThat(PrerequisiteCheck.check(rules, List.of(took("2025 Fall", "CMPT", "225", "C-"))).met()).isTrue();
    }

    @Test
    void courseNotTakenIsNamed() {
        requires(null, "CMPT", "225", "C-");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of());

        assertThat(result.met()).isFalse();
        assertThat(result.missing()).containsExactly("Needs CMPT 225 with C- or better.");
    }

    // #39 The message says which grade is needed and which grade the student has
    @Test
    void gradeBelowTheMinimumSaysWhatTheStudentHas() {
        requires(null, "CMPT", "225", "C-");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules,
                List.of(took("2025 Fall", "CMPT", "225", "D")));

        assertThat(result.met()).isFalse();
        assertThat(result.missing()).containsExactly("Needs CMPT 225 with C- or better, you have D.");
    }

    // With no minimum grade, any pass counts but a fail doesn't
    @Test
    void anyPassCountsWhenThereIsNoMinimumGrade() {
        requires(null, "MACM", "101", null);

        assertThat(PrerequisiteCheck.check(rules, List.of(took("2025 Fall", "MACM", "101", "D"))).met()).isTrue();

        PrerequisiteCheck.Result failed = PrerequisiteCheck.check(rules,
                List.of(took("2025 Fall", "MACM", "101", "F")));
        assertThat(failed.met()).isFalse();
        assertThat(failed.missing()).containsExactly("Needs MACM 101, you have F.");
    }

    // A retake that reaches the grade counts, whichever order the attempts are in
    @Test
    void bestAttemptCounts() {
        requires(null, "CMPT", "225", "C-");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of(
                took("2025 Spring", "CMPT", "225", "D"),
                took("2025 Fall", "CMPT", "225", "B+")));

        assertThat(result.met()).isTrue();
    }

    @Test
    void transferCreditMeetsAnyMinimumGrade() {
        requires(null, "CMPT", "225", "C-");

        assertThat(PrerequisiteCheck.check(rules,
                List.of(took("2024 Fall", "CMPT", "225", Grades.TRANSFER))).met()).isTrue();
    }

    // A prerequisite in progress counts, but the student is told it still has to be finished
    @Test
    void prerequisiteInProgressCountsButIsFlagged() {
        requires(null, "CMPT", "225", "C-");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules,
                List.of(took("2026 Fall", "CMPT", "225", null)));

        assertThat(result.met()).isTrue();
        assertThat(result.toConfirm())
                .containsExactly("CMPT 225 is still in progress, and you need C- or better.");
    }

    @Test
    void courseCodesMatchIgnoringCase() {
        requires(null, "CMPT", "225", "C-");

        assertThat(PrerequisiteCheck.check(rules, List.of(took("2025 Fall", "cmpt", "225", "A"))).met()).isTrue();
    }

    @Test
    void everyPartOfAnAndGroupIsNeeded() {
        RequisiteRule all = group(null, NodeType.AND);
        requires(all, "CMPT", "225", "C-");
        requires(all, "MACM", "101", "C-");
        requires(all, "MATH", "151", null);

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules,
                List.of(took("2025 Fall", "CMPT", "225", "A")));

        assertThat(result.met()).isFalse();
        assertThat(result.missing()).containsExactly(
                "Needs MACM 101 with C- or better.",
                "Needs MATH 151.");
    }

    @Test
    void onePartOfAnOrGroupIsEnough() {
        RequisiteRule either = group(null, NodeType.OR);
        requires(either, "MATH", "151", null);
        requires(either, "MATH", "150", null);

        assertThat(PrerequisiteCheck.check(rules, List.of(took("2025 Fall", "MATH", "150", "B"))).met()).isTrue();
    }

    @Test
    void unmetOrGroupListsEveryOption() {
        RequisiteRule either = group(null, NodeType.OR);
        requires(either, "CMPT", "125", "C-");
        requires(either, "CMPT", "135", null);

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules,
                List.of(took("2025 Fall", "CMPT", "125", "D")));

        assertThat(result.met()).isFalse();
        assertThat(result.missing())
                .containsExactly("Needs CMPT 125 with C- or better (you have D) or CMPT 135.");
    }

    // CMPT 276: CMPT 225, (MACM 101 or (ENSC 251 and ENSC 252)) and (MATH 151 or MATH 150)
    @Test
    void nestedGroupsOnlyNameWhatIsStillMissing() {
        RequisiteRule all = group(null, NodeType.AND);
        requires(all, "CMPT", "225", null);
        RequisiteRule discrete = group(all, NodeType.OR);
        requires(discrete, "MACM", "101", null);
        RequisiteRule ensc = group(discrete, NodeType.AND);
        requires(ensc, "ENSC", "251", null);
        requires(ensc, "ENSC", "252", null);
        RequisiteRule calculus = group(all, NodeType.OR);
        requires(calculus, "MATH", "151", null);
        requires(calculus, "MATH", "150", null);

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of(
                took("2025 Fall", "CMPT", "225", "B"),
                took("2025 Fall", "ENSC", "251", "B")));

        assertThat(result.met()).isFalse();
        assertThat(result.missing()).containsExactly(
                "Needs MACM 101 or ENSC 252.",
                "Needs MATH 151 or MATH 150.");
    }

    @Test
    void nestedGroupsAreMetWhenEveryBranchIs() {
        RequisiteRule all = group(null, NodeType.AND);
        requires(all, "CMPT", "225", null);
        RequisiteRule discrete = group(all, NodeType.OR);
        requires(discrete, "MACM", "101", null);
        RequisiteRule ensc = group(discrete, NodeType.AND);
        requires(ensc, "ENSC", "251", null);
        requires(ensc, "ENSC", "252", null);

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of(
                took("2025 Fall", "CMPT", "225", "B"),
                took("2025 Fall", "ENSC", "251", "B"),
                took("2026 Spring", "ENSC", "252", "C")));

        assertThat(result.met()).isTrue();
        assertThat(result.missing()).isEmpty();
    }

    // #50 The unit total decides "N units" prerequisites
    @Test
    void unitRequirementIsMetByEarnedUnits() {
        requiresUnits(null, "6");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of(
                took("2025 Fall", "CMPT", "120", "A"),
                took("2025 Fall", "MATH", "151", "B")));

        assertThat(result.met()).isTrue();
        assertThat(result.toConfirm()).isEmpty();
    }

    @Test
    void unitRequirementSaysHowManyUnitsTheStudentHas() {
        requiresUnits(null, "60");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of(
                took("2025 Fall", "CMPT", "120", "A"),
                took("2025 Fall", "MATH", "151", "F")));

        assertThat(result.met()).isFalse();
        assertThat(result.missing()).containsExactly("Needs 60 units, you have 3.00.");
    }

    @Test
    void unitRequirementCountsCoursesInProgressButIsFlagged() {
        requiresUnits(null, "6");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of(
                took("2025 Fall", "CMPT", "120", "A"),
                took("2026 Fall", "CMPT", "125", null)));

        assertThat(result.met()).isTrue();
        assertThat(result.toConfirm())
                .containsExactly("Needs 6 units, which you'll have after your courses in progress.");
    }

    // "One W course" can't be checked yet, so it doesn't block the course but is shown to the student
    @Test
    void requirementTheAppCantCheckIsPassedOnToConfirm() {
        RequisiteRule all = group(null, NodeType.AND);
        requires(all, "CMPT", "225", null);
        special(all, "One W course");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules,
                List.of(took("2025 Fall", "CMPT", "225", "B")));

        assertThat(result.met()).isTrue();
        assertThat(result.toConfirm()).containsExactly("One W course");
    }

    // Otherwise "CMPT 125 or permission of the instructor" would let everyone in
    @Test
    void requirementTheAppCantCheckNeverSatisfiesAnOrGroup() {
        RequisiteRule either = group(null, NodeType.OR);
        requires(either, "CMPT", "125", null);
        special(either, "permission of the instructor");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of());

        assertThat(result.met()).isFalse();
        assertThat(result.missing()).containsExactly("Needs CMPT 125 or permission of the instructor.");
    }

    @Test
    void courseWithOnlyAnUncheckableRequirementIsLeftToTheStudent() {
        special(null, "Permission of the department");

        PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, List.of());

        assertThat(result.met()).isTrue();
        assertThat(result.missing()).isEmpty();
        assertThat(result.toConfirm()).containsExactly("Permission of the department");
    }
}