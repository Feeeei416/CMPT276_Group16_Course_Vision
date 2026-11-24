package cmpt276.group16.graduation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import cmpt276.group16.graduation.RequirementFile.CourseEntry;
import cmpt276.group16.graduation.RequirementFile.RuleEntry;
import cmpt276.group16.graduation.RequirementFile.SetEntry;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementKind;
import cmpt276.group16.services.PlanRules;

// Checks the real requirements.json against the SFU Calendar, and against mistakes the file format can't catch,
// like a required course that would also count as an elective. docs/graduation-requirements.md lists the sources
class RealRequirementsTest {

    private static final RequirementFile FILE =
            new RequirementFile(new ClassPathResource("requirements/requirements.json"));

    private static SetEntry set(Program program, int year) {
        return FILE.sets().stream()
                .filter(set -> set.program() == program && set.entryYear() == year)
                .findFirst().orElseThrow();
    }

    private static List<RuleEntry> rules(SetEntry set, String category, RequirementKind kind) {
        return set.requirements().stream()
                .filter(rule -> rule.category().equals(category) && rule.kind() == kind)
                .toList();
    }

    private static RuleEntry onlyRule(SetEntry set, String category, RequirementKind kind) {
        List<RuleEntry> found = rules(set, category, kind);
        assertThat(found).as(set.name() + ", " + category + ", " + kind).hasSize(1);
        return found.get(0);
    }

    // The code of each COURSE requirement in a category
    private static List<String> requiredCourses(SetEntry set, String category) {
        return rules(set, category, RequirementKind.COURSE).stream()
                .map(rule -> rule.courses().get(0).code()).toList();
    }

    private static List<String> codes(RuleEntry rule, RequirementCourse.Use use) {
        return rule.courses().stream().filter(course -> course.use() == use).map(CourseEntry::code).toList();
    }

    @Test
    void everyProgramAndYearHasASet() {
        assertThat(FILE.sets()).extracting(SetEntry::name).containsExactlyInAnyOrder(
                "Computing Science 2023", "Computing Science 2024",
                "Computer Engineering 2023", "Computer Engineering 2024");
    }

    @Test
    void everySetLinksToItsOwnCalendarYear() {
        for (SetEntry set : FILE.sets()) {
            assertThat(set.calendarUrl()).as(set.name())
                    .startsWith("https://www.sfu.ca/students/calendar/" + set.entryYear() + "/");
        }
    }

    @Test
    void computingScience2024HasTenRequiredLowerDivisionCourses() {
        assertThat(requiredCourses(set(Program.COMPUTING_SCIENCE, 2024), "Lower division")).containsExactly(
                "CMPT 105W", "CMPT 120", "CMPT 125", "CMPT 201", "CMPT 210", "CMPT 225", "CMPT 276", "CMPT 295",
                "MACM 101", "STAT 271");
    }

    @Test
    void computingScience2023HasNoCmpt201() {
        List<String> courses = requiredCourses(set(Program.COMPUTING_SCIENCE, 2023), "Lower division");

        assertThat(courses).hasSize(9).doesNotContain("CMPT 201");
    }

    @Test
    void breadthNeedsFiveOfSixAreasAndCmpt307() {
        RuleEntry breadth = onlyRule(set(Program.COMPUTING_SCIENCE, 2024), "Breadth", RequirementKind.AREAS);

        assertThat(breadth.count()).isEqualTo(5);
        assertThat(breadth.courses().stream().map(CourseEntry::area).distinct()).hasSize(6);
        assertThat(codes(breadth, RequirementCourse.Use.REQUIRED)).containsExactly("CMPT 307");
        assertThat(breadth.exclusive()).isTrue();
    }

    @Test
    void breadth2023NeedsCmpt300And307() {
        RuleEntry breadth = onlyRule(set(Program.COMPUTING_SCIENCE, 2023), "Breadth", RequirementKind.AREAS);

        assertThat(codes(breadth, RequirementCourse.Use.REQUIRED))
                .containsExactlyInAnyOrder("CMPT 300", "CMPT 303", "CMPT 307");
    }

    // CMPT 303 replaced CMPT 300, so both are required courses in Computing Systems, which makes either one count.
    // Required courses in the same area are alternatives; each area with any must be covered by one of them
    @Test
    void breadth2023TakesCmpt300OrCmpt303() {
        RuleEntry breadth = onlyRule(set(Program.COMPUTING_SCIENCE, 2023), "Breadth", RequirementKind.AREAS);

        assertThat(breadth.courses().stream().filter(course -> course.use() == RequirementCourse.Use.REQUIRED)
                .map(course -> course.area() + ": " + course.code()))
                .containsExactlyInAnyOrder("Computing Systems: CMPT 300", "Computing Systems: CMPT 303",
                        "Theoretical Computing Science: CMPT 307");
    }

    @Test
    void computingSystemsHasCmpt303In2024AndBothIn2023() {
        assertThat(computingSystems(2024)).contains("CMPT 303").doesNotContain("CMPT 300");
        assertThat(computingSystems(2023)).contains("CMPT 300", "CMPT 303");
    }

    private static List<String> computingSystems(int year) {
        RuleEntry breadth = onlyRule(set(Program.COMPUTING_SCIENCE, year), "Breadth", RequirementKind.AREAS);
        return breadth.courses().stream().filter(course -> "Computing Systems".equals(course.area()))
                .map(CourseEntry::code).toList();
    }

    @Test
    void depthIsTwelveUnitsOfCmpt400AndAbove() {
        RuleEntry depth = onlyRule(set(Program.COMPUTING_SCIENCE, 2024), "Depth", RequirementKind.UNITS_FROM);

        assertThat(depth.units()).isEqualByComparingTo("12");
        assertThat(depth.subject()).isEqualTo("CMPT");
        assertThat(depth.minNumber()).isEqualTo(400);
        assertThat(codes(depth, RequirementCourse.Use.EXCLUDED)).containsExactly("CMPT 415", "CMPT 416", "CMPT 498");
    }

    // The BSc tables say "additional", and MACM 316 is already required on its own
    @Test
    void bscTablesDontListMacm316() {
        for (int year : List.of(2023, 2024)) {
            RuleEntry tables = onlyRule(set(Program.COMPUTING_SCIENCE, year), "BSc", RequirementKind.CHOOSE);
            assertThat(tables.count()).isEqualTo(2);
            assertThat(codes(tables, RequirementCourse.Use.LISTED)).doesNotContain("MACM 316").contains("MATH 343");
        }
    }

    // The Calendar numbers the core courses 1 to 37: 33 on their own, plus four choices. Two are in the Calendar
    // (MATH 151 or 150, ENSC 410 or 411) and two are renamed courses, where a student may have either:
    // CMPT 300 became CMPT 303, and ENSC 100W became ENSC 100 in Fall 2026
    @Test
    void computerEngineeringHasThirtySevenCoreCourses() {
        for (int year : List.of(2023, 2024)) {
            SetEntry set = set(Program.COMPUTER_ENGINEERING, year);
            assertThat(requiredCourses(set, "Required courses")).as(set.name()).hasSize(33)
                    .doesNotContain("CMPT 300", "CMPT 303", "ENSC 100W", "ENSC 100");
            List<RuleEntry> choices = rules(set, "Required courses", RequirementKind.CHOOSE);
            assertThat(choices).extracting(RuleEntry::title).containsExactly("Operating systems",
                    "Engineering, science and society", "Calculus I", "Business of engineering",
                    "Constrained electives");
            assertThat(codes(choices.get(0), RequirementCourse.Use.LISTED)).containsExactly("CMPT 300", "CMPT 303");
            assertThat(codes(choices.get(1), RequirementCourse.Use.LISTED)).containsExactly("ENSC 100W", "ENSC 100");
        }
    }

    // Breadth (B) courses "must be outside the student's major subject", so CMPT 120 (B-Sci) doesn't count for
    // Computing Science. Writing and Quantitative have no such rule
    @Test
    void breadthDesignationsLeaveOutTheMajorSubject() {
        for (SetEntry set : FILE.sets()) {
            String major = set.program() == Program.COMPUTING_SCIENCE ? "CMPT" : "ENSC";
            for (RuleEntry rule : set.requirements()) {
                if (rule.kind() != RequirementKind.DESIGNATION) {
                    continue;
                }
                boolean breadth = rule.designations().stream().anyMatch(d -> d.name().startsWith("B_"));
                assertThat(rule.outsideSubject()).as(set.name() + ", " + rule.title())
                        .isEqualTo(breadth ? major : null);
            }
        }
    }

    @Test
    void everySetSaysItsVersion() {
        assertThat(FILE.sets()).extracting(SetEntry::version).allMatch(version -> version >= 1);
    }

    // "Any ENSC 300 or 400 course" would otherwise let a required course or a co-op term count as an elective
    @Test
    void esdElectivesLeaveOutEveryRequiredEnscCourse() {
        for (int year : List.of(2023, 2024)) {
            SetEntry set = set(Program.COMPUTER_ENGINEERING, year);
            RuleEntry esd = onlyRule(set, "ESD electives", RequirementKind.UNITS_FROM);
            List<String> excluded = codes(esd, RequirementCourse.Use.EXCLUDED);

            List<String> upperEnsc = requiredCourses(set, "Required courses").stream()
                    .filter(code -> code.startsWith("ENSC ") && code.charAt(5) >= '3').toList();
            assertThat(excluded).as(set.name()).containsAll(upperEnsc)
                    .contains("ENSC 412", "ENSC 410", "ENSC 411", "ENSC 395", "ENSC 396");
            assertThat(esd.maxCoursesBelow()).isEqualTo(2);
            assertThat(esd.belowNumber()).isEqualTo(400);
        }
    }

    // Co-op work terms only count toward the co-op requirement
    @Test
    void coopCoursesOnlyCountTowardCoop() {
        for (SetEntry set : FILE.sets()) {
            for (RuleEntry rule : set.requirements()) {
                if (rule.category().equals("Co-op")) {
                    continue;
                }
                for (CourseEntry course : rule.courses()) {
                    if (course.use() != RequirementCourse.Use.EXCLUDED) {
                        assertThat(PlanRules.isCoopCourse(course.subject(), course.number()))
                                .as(set.name() + ", " + rule.category() + ": " + course.code()).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void everyWqbRuleNeedsCMinus() {
        for (SetEntry set : FILE.sets()) {
            set.requirements().stream().filter(rule -> rule.kind() == RequirementKind.DESIGNATION)
                    .forEach(rule -> assertThat(rule.minGrade()).as(set.name() + ", " + rule.title()).isEqualTo("C-"));
        }
    }

    @Test
    void computingScienceNeeds120UnitsAnd45UpperDivision() {
        for (int year : List.of(2023, 2024)) {
            SetEntry set = set(Program.COMPUTING_SCIENCE, year);
            assertThat(onlyRule(set, "Totals and notes", RequirementKind.TOTAL_UNITS).units()).isEqualByComparingTo("120");
            RuleEntry upper = onlyRule(set, "Upper division", RequirementKind.TOTAL_UNITS);
            assertThat(upper.units()).isEqualByComparingTo("45");
            assertThat(upper.minNumber()).isEqualTo(300);
        }
    }

    // The Calendar gives no total for Computer Engineering, so there's a note instead
    @Test
    void computerEngineeringHasNoTotalUnits() {
        for (int year : List.of(2023, 2024)) {
            SetEntry set = set(Program.COMPUTER_ENGINEERING, year);
            assertThat(set.requirements()).noneMatch(rule -> rule.kind() == RequirementKind.TOTAL_UNITS);
        }
    }
}
