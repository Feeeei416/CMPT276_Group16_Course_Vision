package cmpt276.group16.graduation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import cmpt276.group16.graduation.GraduationReport.Result;
import cmpt276.group16.graduation.GraduationReport.Status;
import cmpt276.group16.graduation.GraduationRequirements.RequirementWithCourses;
import cmpt276.group16.graduation.GraduationRequirements.StudentRequirements;
import cmpt276.group16.graduation.StudentCourse.State;
import cmpt276.group16.models.Designation;
import cmpt276.group16.models.GraduationRequirement;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementCourse.Use;
import cmpt276.group16.models.RequirementKind;
import cmpt276.group16.models.RequirementSet;

// #69: RequirementCheck on its own, with requirements and courses built by hand.
// GraduationReportControllerTest checks the whole page against the real requirements file
class RequirementCheckTest {

    private final RequirementSet set = new RequirementSet(Program.COMPUTING_SCIENCE, 2024, "https://www.sfu.ca");
    private final List<RequirementWithCourses> requirements = new ArrayList<>();

    // ---- Requirements ----

    private GraduationRequirement rule(String category, RequirementKind kind) {
        return new GraduationRequirement(set, requirements.size(), category, kind);
    }

    private void add(GraduationRequirement rule, RequirementCourse... courses) {
        requirements.add(new RequirementWithCourses(rule, List.of(courses)));
    }

    private static RequirementCourse named(GraduationRequirement rule, String code, Use use) {
        String[] parts = code.split(" ");
        return new RequirementCourse(rule, parts[0], parts[1], use);
    }

    private static RequirementCourse inArea(GraduationRequirement rule, String code, String area, Use use) {
        RequirementCourse course = named(rule, code, use);
        course.setArea(area);
        return course;
    }

    private void requiredCourse(String code) {
        GraduationRequirement rule = rule("Lower division", RequirementKind.COURSE);
        add(rule, named(rule, code, Use.LISTED));
    }

    private void choose(String title, int count, boolean exclusive, String... codes) {
        GraduationRequirement rule = rule("Lower division", RequirementKind.CHOOSE);
        rule.setTitle(title);
        rule.setCourseCount(count);
        rule.setExclusive(exclusive);
        add(rule, List.of(codes).stream().map(code -> named(rule, code, Use.LISTED)).toArray(RequirementCourse[]::new));
    }

    // Depth: 12 units of CMPT 400 and above, not CMPT 415
    private void depth() {
        GraduationRequirement rule = rule("Depth", RequirementKind.UNITS_FROM);
        rule.setTitle("CMPT 400 and above");
        rule.setUnits(new BigDecimal("12"));
        rule.setSubject("CMPT");
        rule.setMinNumber(400);
        rule.setExclusive(true);
        add(rule, named(rule, "CMPT 415", Use.EXCLUDED));
    }

    // Breadth: 3 courses from 3 of 4 areas, including CMPT 307
    private void breadth() {
        GraduationRequirement rule = rule("Breadth", RequirementKind.AREAS);
        rule.setTitle("Breadth");
        rule.setCourseCount(3);
        rule.setExclusive(true);
        add(rule,
                inArea(rule, "CMPT 310", "AI", Use.LISTED),
                inArea(rule, "CMPT 410", "AI", Use.LISTED),
                inArea(rule, "CMPT 361", "Visual", Use.LISTED),
                inArea(rule, "CMPT 371", "Systems", Use.LISTED),
                inArea(rule, "CMPT 307", "Theory", Use.REQUIRED),
                inArea(rule, "CMPT 308", "Theory", Use.LISTED));
    }

    private void designation(String title, Designation designation, String outsideSubject) {
        GraduationRequirement rule = rule("WQB", RequirementKind.DESIGNATION);
        rule.setTitle(title);
        rule.setUnits(new BigDecimal("6"));
        rule.setDesignations(Set.of(designation));
        rule.setOutsideSubject(outsideSubject);
        rule.setMinGrade("C-");
        add(rule);
    }

    private void totalUnits(String units, Integer minNumber) {
        GraduationRequirement rule = rule("Totals", RequirementKind.TOTAL_UNITS);
        rule.setTitle(minNumber == null ? "Total units" : "Upper-division units");
        rule.setUnits(new BigDecimal(units));
        rule.setMinNumber(minNumber);
        add(rule);
    }

    // ---- Courses ----

    private static StudentCourse done(String code, String grade, Designation... designations) {
        return course(code, State.COMPLETED, grade, designations);
    }

    private static StudentCourse taking(String code) {
        return course(code, State.IN_PROGRESS, null);
    }

    private static StudentCourse planned(String code) {
        return course(code, State.PLANNED, null);
    }

    private static StudentCourse course(String code, State state, String grade, Designation... designations) {
        String[] parts = code.split(" ");
        Set<Designation> set = designations.length == 0 ? Set.of() : EnumSet.copyOf(List.of(designations));
        return new StudentCourse(parts[0], parts[1], new BigDecimal("3"), state, grade, set);
    }

    private GraduationReport report(StudentCourse... courses) {
        return RequirementCheck.report(new StudentRequirements(set, requirements), List.of(courses));
    }

    // The result for the requirement added nth, counting from 0
    private static Result result(GraduationReport report, int index) {
        return report.categories().stream().flatMap(category -> category.results().stream()).toList().get(index);
    }

    private static List<String> counted(Result result) {
        return result.courses().stream().map(StudentCourse::label).toList();
    }

    // ---- Required courses and choices ----

    @Test
    void requiredCourseShowsHowFarAlongItIs() {
        requiredCourse("CMPT 225");
        requiredCourse("CMPT 276");
        requiredCourse("CMPT 295");
        requiredCourse("CMPT 201");

        GraduationReport report = report(done("CMPT 225", "A"), taking("CMPT 276"), planned("CMPT 295"));

        assertThat(result(report, 0).status()).isEqualTo(Status.COMPLETED);
        assertThat(counted(result(report, 0))).containsExactly("CMPT 225 (A)");
        assertThat(result(report, 1).status()).isEqualTo(Status.IN_PROGRESS);
        assertThat(counted(result(report, 1))).containsExactly("CMPT 276 (in progress)");
        assertThat(result(report, 2).status()).isEqualTo(Status.PLANNED);
        assertThat(counted(result(report, 2))).containsExactly("CMPT 295 (planned)");
        assertThat(result(report, 3).status()).isEqualTo(Status.REMAINING);
        assertThat(result(report, 3).name()).isEqualTo("CMPT 201");
        assertThat(result(report, 3).courses()).isEmpty();
    }

    @Test
    void choiceUsesACompletedCourseBeforeAPlannedOne() {
        choose("Calculus I", 1, false, "MATH 150", "MATH 151");

        Result calculus = result(report(planned("MATH 150"), done("MATH 151", "B")), 0);

        assertThat(calculus.status()).isEqualTo(Status.COMPLETED);
        assertThat(counted(calculus)).containsExactly("MATH 151 (B)");
        assertThat(calculus.progress()).isNull();
        // A completed choice doesn't list the other options
        assertThat(calculus.options()).isNull();
    }

    @Test
    void unmetChoiceListsItsOptions() {
        choose("Calculus I", 1, false, "MATH 150", "MATH 151");

        Result calculus = result(report(), 0);

        assertThat(calculus.status()).isEqualTo(Status.REMAINING);
        assertThat(calculus.options()).isEqualTo("MATH 150, MATH 151");
    }

    @Test
    void choiceOfTwoCountsCourses() {
        choose("Two more courses", 2, false, "CMPT 310", "CMPT 361", "CMPT 371");

        Result result = result(report(done("CMPT 310", "A")), 0);

        assertThat(result.status()).isEqualTo(Status.REMAINING);
        assertThat(result.progress()).isEqualTo("1 of 2 courses");
    }

    @Test
    void longChoiceDoesntListItsOptions() {
        choose("Table courses", 1, false, "CMPT 310", "CMPT 340", "CMPT 361", "CMPT 363", "CMPT 365",
                "CMPT 371", "CMPT 373", "CMPT 379", "CMPT 383");

        assertThat(result(report(), 0).options()).isNull();
    }

    // ---- Units from a range ----

    @Test
    void depthCountsUnitsFromTheRangeButNotExcludedCourses() {
        depth();

        Result depth = result(report(done("CMPT 405", "A"), done("CMPT 415", "A"), done("CMPT 354", "A"),
                taking("CMPT 412")), 0);

        assertThat(depth.status()).isEqualTo(Status.REMAINING);
        assertThat(depth.progress()).isEqualTo("6 of 12 units");
        assertThat(counted(depth)).containsExactly("CMPT 405 (A)", "CMPT 412 (in progress)");
    }

    @Test
    void depthMetByPlannedCoursesIsPlanned() {
        depth();

        Result depth = result(report(done("CMPT 405", "A"), done("CMPT 410", "B"), taking("CMPT 412"),
                planned("CMPT 431"), planned("CMPT 470")), 0);

        assertThat(depth.status()).isEqualTo(Status.PLANNED);
        assertThat(depth.progress()).isEqualTo("12 of 12 units");
        // Only what's needed is used, so the other planned course is free for another rule
        assertThat(counted(depth)).containsExactly("CMPT 405 (A)", "CMPT 410 (B)", "CMPT 412 (in progress)",
                "CMPT 431 (planned)");
    }

    @Test
    void atMostTwoLowerCoursesCountWhenTheRuleSaysSo() {
        GraduationRequirement rule = rule("ESD electives", RequirementKind.UNITS_FROM);
        rule.setUnits(new BigDecimal("12"));
        rule.setSubject("ENSC");
        rule.setMinNumber(300);
        rule.setLimitBelow(2, 400);
        add(rule);

        Result esd = result(report(done("ENSC 320", "A"), done("ENSC 350", "A"), done("ENSC 380", "A"),
                done("ENSC 405W", "A")), 0);

        assertThat(esd.status()).isEqualTo(Status.REMAINING);
        assertThat(esd.progress()).isEqualTo("9 of 12 units");
        assertThat(counted(esd)).containsExactly("ENSC 320 (A)", "ENSC 350 (A)", "ENSC 405W (A)");
    }

    @Test
    void coopWorkTermsNeverCountTowardUnits() {
        GraduationRequirement rule = rule("ESD electives", RequirementKind.UNITS_FROM);
        rule.setUnits(new BigDecimal("6"));
        rule.setSubject("ENSC");
        rule.setMinNumber(300);
        add(rule);

        Result esd = result(report(done("ENSC 395", "P"), done("ENSC 396", "P")), 0);

        assertThat(esd.status()).isEqualTo(Status.REMAINING);
        assertThat(esd.courses()).isEmpty();
    }

    // ---- Breadth areas ----

    @Test
    void breadthNeedsTheRequiredCourse() {
        breadth();

        Result breadthResult = result(report(done("CMPT 310", "A"), done("CMPT 361", "A"), done("CMPT 371", "A")), 0);

        assertThat(breadthResult.status()).isEqualTo(Status.REMAINING);
        assertThat(breadthResult.progress()).isEqualTo("2 of 3 areas, still needs CMPT 307");
    }

    @Test
    void breadthUsesOneCourseFromEachArea() {
        breadth();

        Result breadthResult = result(report(done("CMPT 310", "A"), done("CMPT 410", "A"), done("CMPT 307", "B"),
                planned("CMPT 361")), 0);

        assertThat(breadthResult.status()).isEqualTo(Status.PLANNED);
        assertThat(breadthResult.progress()).isEqualTo("3 of 3 areas");
        // CMPT 410 is in the same area as CMPT 310, so the planned Visual course is needed
        assertThat(counted(breadthResult)).containsExactly("CMPT 307 (B)", "CMPT 310 (A)", "CMPT 361 (planned)");
    }

    @Test
    void anotherCourseInTheRequiredCoursesAreaDoesntCoverIt() {
        breadth();

        Result breadthResult = result(report(done("CMPT 308", "A"), done("CMPT 310", "A"), done("CMPT 361", "A"),
                done("CMPT 371", "A")), 0);

        assertThat(breadthResult.status()).isEqualTo(Status.REMAINING);
        assertThat(breadthResult.progress()).isEqualTo("2 of 3 areas, still needs CMPT 307");
    }

    // ---- Counting a course once ----

    @Test
    void courseUsedForBreadthDoesntCountForDepth() {
        breadth();
        depth();

        GraduationReport report = report(done("CMPT 307", "A"), done("CMPT 310", "A"), done("CMPT 410", "A"),
                done("CMPT 471", "A"), done("CMPT 405", "A"), done("CMPT 412", "A"), done("CMPT 361", "A"));

        // Breadth takes the 300-level courses first, so the 400-level ones are left for Depth
        assertThat(counted(result(report, 0))).containsExactly("CMPT 307 (A)", "CMPT 310 (A)", "CMPT 361 (A)");
        assertThat(result(report, 1).status()).isEqualTo(Status.COMPLETED);
        assertThat(counted(result(report, 1)))
                .containsExactly("CMPT 405 (A)", "CMPT 410 (A)", "CMPT 412 (A)", "CMPT 471 (A)");
    }

    @Test
    void exclusiveChoiceCantReuseABreadthCourse() {
        breadth();
        choose("Two more courses", 2, true, "CMPT 307", "CMPT 310", "CMPT 405");

        GraduationReport report = report(done("CMPT 307", "A"), done("CMPT 310", "A"), done("CMPT 361", "A"),
                done("CMPT 405", "A"));

        assertThat(result(report, 1).status()).isEqualTo(Status.REMAINING);
        assertThat(counted(result(report, 1))).containsExactly("CMPT 405 (A)");
    }

    @Test
    void requiredCoursesCanAlsoCountTowardExclusiveRules() {
        requiredCourse("CMPT 307");
        breadth();

        GraduationReport report = report(done("CMPT 307", "A"), done("CMPT 310", "A"), done("CMPT 361", "A"));

        assertThat(result(report, 0).status()).isEqualTo(Status.COMPLETED);
        assertThat(result(report, 1).status()).isEqualTo(Status.COMPLETED);
    }

    // ---- WQB ----

    @Test
    void writingCountsDesignatedCoursesWithCMinusOrBetter() {
        designation("Writing", Designation.W, null);

        Result writing = result(report(done("CMPT 105W", "D", Designation.W), done("CMPT 376W", "C-", Designation.W),
                planned("ENGL 199W")), 0);

        assertThat(writing.status()).isEqualTo(Status.REMAINING);
        assertThat(writing.progress()).isEqualTo("3 of 6 units");
        assertThat(counted(writing)).containsExactly("CMPT 376W (C-)");
    }

    @Test
    void breadthDesignationsLeaveOutTheMajor() {
        designation("Breadth, Sciences", Designation.B_SCI, "CMPT");

        Result science = result(report(done("CMPT 120", "A", Designation.B_SCI, Designation.Q),
                done("PHYS 101", "B", Designation.B_SCI), taking("CHEM 121")), 0);

        assertThat(science.status()).isEqualTo(Status.REMAINING);
        assertThat(counted(science)).containsExactly("PHYS 101 (B)");
    }

    @Test
    void designationsCanShareCoursesWithOtherRules() {
        requiredCourse("CMPT 376W");
        designation("Writing", Designation.W, null);

        GraduationReport report = report(done("CMPT 376W", "A", Designation.W), done("CMPT 105W", "B", Designation.W));

        assertThat(result(report, 0).status()).isEqualTo(Status.COMPLETED);
        assertThat(result(report, 1).status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void transferCreditAndPassMeetTheMinimum() {
        designation("Quantitative", Designation.Q, null);

        Result quantitative = result(report(done("MATH 151", "TR", Designation.Q), done("STAT 270", "P", Designation.Q)), 0);

        assertThat(quantitative.status()).isEqualTo(Status.COMPLETED);
    }

    // ---- Unit totals ----

    @Test
    void totalUnitsSaysWhatIsDoneInProgressAndPlanned() {
        totalUnits("12", null);

        Result total = result(report(done("CMPT 120", "A"), done("CMPT 125", "A"), taking("CMPT 225"),
                planned("CMPT 276"), planned("CMPT 426")), 0);

        // CMPT 426 is a co-op work term, so it doesn't count
        assertThat(total.status()).isEqualTo(Status.PLANNED);
        assertThat(total.progress()).isEqualTo("6 of 12 units completed, 3 in progress, 3 planned");
        assertThat(total.courses()).isEmpty();
    }

    @Test
    void upperDivisionUnitsOnlyCountCoursesNumbered300AndUp() {
        totalUnits("6", 300);

        Result upper = result(report(done("CMPT 225", "A"), done("CMPT 307", "A"), done("CMPT 405", "B")), 0);

        assertThat(upper.status()).isEqualTo(Status.COMPLETED);
        assertThat(upper.progress()).isEqualTo("6 of 6 units completed");
    }

    @Test
    void reportTotalsLeaveOutCoopAndSayWhatsStillNeeded() {
        totalUnits("120", null);
        totalUnits("45", 300);

        GraduationReport report = report(done("CMPT 120", "A"), taking("CMPT 225"), planned("CMPT 276"),
                done("CMPT 426", "P"));

        assertThat(report.units().completed()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(report.units().inProgress()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(report.units().planned()).isEqualByComparingTo(new BigDecimal("3"));
        assertThat(report.units().required()).isEqualByComparingTo(new BigDecimal("120"));
        assertThat(report.units().needed()).isEqualByComparingTo(new BigDecimal("111"));
    }

    // ---- Notes and grouping ----

    @Test
    void notesAreLeftForAnAdvisorToCheck() {
        GraduationRequirement rule = rule("Totals", RequirementKind.NOTE);
        rule.setText("An upper-division GPA of 2.00.");
        add(rule);

        Result note = result(report(), 0);

        assertThat(note.status()).isEqualTo(Status.CHECK);
        assertThat(note.name()).isEqualTo("An upper-division GPA of 2.00.");
    }

    @Test
    void requirementsAreGroupedUnderTheirHeadingsInOrder() {
        requiredCourse("CMPT 225");
        breadth();
        requiredCourse("CMPT 276");
        depth();

        GraduationReport report = report();

        assertThat(report.categories().stream().map(GraduationReport.Category::name).toList())
                .containsExactly("Lower division", "Breadth", "Depth");
        assertThat(report.categories().get(0).results()).hasSize(2);
    }

    @Test
    void reportWithoutATotalHasNothingNeeded() {
        requiredCourse("CMPT 225");

        assertThat(report().units().needed()).isNull();
    }
}
