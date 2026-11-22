package cmpt276.group16.graduation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import cmpt276.group16.graduation.RequirementFile.CourseEntry;
import cmpt276.group16.graduation.RequirementFile.RuleEntry;
import cmpt276.group16.graduation.RequirementFile.SetEntry;
import cmpt276.group16.models.Designation;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementKind;

class RequirementFileTest {

    // One set holding the given requirements, as they'd be written in the file
    private static String setWith(String requirements) {
        return """
                { "sets": [ { "program": "COMPUTING_SCIENCE", "entryYear": 2024,
                              "calendarUrl": "https://www.sfu.ca/students/calendar",
                              "requirements": [ %s ] } ] }
                """.formatted(requirements);
    }

    private static List<RuleEntry> rulesOf(String requirements) {
        return RequirementFile.fromJson(setWith(requirements)).sets().get(0).requirements();
    }

    private static RuleEntry onlyRule(String requirement) {
        List<RuleEntry> rules = rulesOf(requirement);
        assertThat(rules).hasSize(1);
        return rules.get(0);
    }

    @Test
    void setKeepsItsProgramYearAndLink() {
        SetEntry set = RequirementFile.fromJson(setWith("""
                { "category": "Totals", "kind": "TOTAL_UNITS", "units": 120 }
                """)).sets().get(0);

        assertThat(set.program()).isEqualTo(Program.COMPUTING_SCIENCE);
        assertThat(set.entryYear()).isEqualTo(2024);
        assertThat(set.calendarUrl()).isEqualTo("https://www.sfu.ca/students/calendar");
        assertThat(set.name()).isEqualTo("Computing Science 2024");
    }

    @Test
    void courseEntryBecomesOneRequirementPerCourse() {
        List<RuleEntry> rules = rulesOf("""
                { "category": "Lower division", "kind": "COURSE", "courses": ["cmpt 120", "CMPT125", "CMPT 105W"] }
                """);

        assertThat(rules).hasSize(3);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.kind()).isEqualTo(RequirementKind.COURSE);
            assertThat(rule.category()).isEqualTo("Lower division");
        });
        assertThat(rules).extracting(rule -> rule.courses().get(0).code())
                .containsExactly("CMPT 120", "CMPT 125", "CMPT 105W");
    }

    @Test
    void chooseKeepsItsCountTitleAndCourses() {
        RuleEntry rule = onlyRule("""
                { "category": "Lower division", "kind": "CHOOSE", "title": "Calculus I", "count": 1,
                  "courses": ["MATH 150", "MATH 151"] }
                """);

        assertThat(rule.title()).isEqualTo("Calculus I");
        assertThat(rule.count()).isEqualTo(1);
        assertThat(rule.courses()).extracting(CourseEntry::code).containsExactly("MATH 150", "MATH 151");
        assertThat(rule.courses()).extracting(CourseEntry::use).containsOnly(RequirementCourse.Use.LISTED);
    }

    @Test
    void unitsFromKeepsItsRangeExclusionsAndLimit() {
        RuleEntry rule = onlyRule("""
                { "category": "ESD electives", "kind": "UNITS_FROM", "units": 12, "exclusive": true,
                  "courses": ["CMPT 305"], "subject": "ensc", "minNumber": 300, "excluded": ["ENSC 412"],
                  "maxCoursesBelow": 2, "belowNumber": 400 }
                """);

        assertThat(rule.units()).isEqualByComparingTo("12");
        assertThat(rule.subject()).isEqualTo("ENSC");
        assertThat(rule.minNumber()).isEqualTo(300);
        assertThat(rule.maxCoursesBelow()).isEqualTo(2);
        assertThat(rule.belowNumber()).isEqualTo(400);
        assertThat(rule.exclusive()).isTrue();
        assertThat(rule.courses()).extracting(CourseEntry::code, CourseEntry::use).containsExactly(
                tuple("CMPT 305", RequirementCourse.Use.LISTED),
                tuple("ENSC 412", RequirementCourse.Use.EXCLUDED));
    }

    @Test
    void unitsFromWorksWithOnlyARange() {
        RuleEntry rule = onlyRule("""
                { "category": "Depth", "kind": "UNITS_FROM", "units": 12, "subject": "CMPT", "minNumber": 400 }
                """);

        assertThat(rule.courses()).isEmpty();
        assertThat(rule.maxCoursesBelow()).isNull();
    }

    @Test
    void areasKeepEachCoursesAreaAndMarkRequiredOnes() {
        RuleEntry rule = onlyRule("""
                { "category": "Breadth", "kind": "AREAS", "count": 2, "exclusive": true, "required": ["CMPT 307"],
                  "areas": { "Artificial Intelligence": ["CMPT 310", "CMPT 410"],
                             "Theoretical Computing Science": ["CMPT 307", "MACM 300"] } }
                """);

        assertThat(rule.count()).isEqualTo(2);
        assertThat(rule.courses()).extracting(CourseEntry::code)
                .containsExactly("CMPT 310", "CMPT 410", "CMPT 307", "MACM 300");
        CourseEntry cmpt307 = rule.courses().get(2);
        assertThat(cmpt307.use()).isEqualTo(RequirementCourse.Use.REQUIRED);
        assertThat(cmpt307.area()).isEqualTo("Theoretical Computing Science");
        assertThat(rule.courses().get(0).use()).isEqualTo(RequirementCourse.Use.LISTED);
        assertThat(rule.courses().get(0).area()).isEqualTo("Artificial Intelligence");
    }

    @Test
    void designationAcceptsBothSpellings() {
        RuleEntry rule = onlyRule("""
                { "category": "WQB", "kind": "DESIGNATION", "units": 9, "designations": ["B-Hum", "B_SOC"],
                  "minGrade": "C-" }
                """);

        assertThat(rule.designations()).containsExactlyInAnyOrder(Designation.B_HUM, Designation.B_SOC);
        assertThat(rule.minGrade()).isEqualTo("C-");
    }

    @Test
    void totalUnitsKeepsMinNumberForUpperDivision() {
        RuleEntry rule = onlyRule("""
                { "category": "Upper division", "kind": "TOTAL_UNITS", "units": 45, "minNumber": 300 }
                """);

        assertThat(rule.units()).isEqualByComparingTo("45");
        assertThat(rule.minNumber()).isEqualTo(300);
    }

    @Test
    void noteKeepsItsText() {
        RuleEntry rule = onlyRule("""
                { "category": "Notes", "kind": "NOTE", "text": "An upper-division GPA of 2.00." }
                """);

        assertThat(rule.text()).isEqualTo("An upper-division GPA of 2.00.");
    }

    // Each mistake stops loading, with a message that names the set, the requirement and the problem
    @ParameterizedTest
    // Written with ' instead of " to keep them readable; the test swaps them back.
    // quoteCharacter is changed so CsvSource doesn't treat the ' as quotes
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "{ 'category': 'X', 'kind': 'PICK', 'courses': ['CMPT 120'] }                  | not a kind of requirement: PICK",
            "{ 'kind': 'NOTE', 'text': 'x' }                                                 | needs \"category\"",
            "{ 'category': 'X', 'kind': 'COURSE', 'courses': ['CMPT 1200'] }                | not a course code: CMPT 1200",
            "{ 'category': 'X', 'kind': 'COURSE' }                                           | needs \"courses\"",
            "{ 'category': 'X', 'kind': 'COURSE', 'courses': [] }                            | needs to be a list of course codes",
            "{ 'category': 'X', 'kind': 'COURSE', 'courses': ['CMPT 120'], 'count': 1 }     | COURSE doesn't use \"count\"",
            "{ 'category': 'X', 'kind': 'CHOOSE', 'courses': ['MATH 150'] }                 | needs \"count\" as a whole number",
            "{ 'category': 'X', 'kind': 'CHOOSE', 'count': 0, 'courses': ['MATH 150'] }     | \"count\" needs to be 1 or more",
            "{ 'category': 'X', 'kind': 'CHOOSE', 'count': 3, 'courses': ['MATH 150'] }     | count is 3 but only 1 courses are listed",
            "{ 'category': 'X', 'kind': 'CHOOSE', 'count': 1, 'courses': ['MATH 150', 'math 150'] } | MATH 150 is listed more than once",
            "{ 'category': 'X', 'kind': 'UNITS_FROM', 'courses': ['CMPT 405'] }             | needs \"units\" above 0",
            "{ 'category': 'X', 'kind': 'UNITS_FROM', 'units': 12 }                          | needs \"courses\", or a \"subject\" and \"minNumber\"",
            "{ 'category': 'X', 'kind': 'UNITS_FROM', 'units': 12, 'subject': 'CMPT' }       | \"subject\" and \"minNumber\" go together",
            "{ 'category': 'X', 'kind': 'UNITS_FROM', 'units': 12, 'subject': 'CMPT', 'minNumber': 40 } | needs to be a course number like 300",
            "{ 'category': 'X', 'kind': 'UNITS_FROM', 'units': 12, 'subject': 'CMPT', 'minNumber': 400, 'maxCoursesBelow': 2 } | \"maxCoursesBelow\" and \"belowNumber\" go together",
            "{ 'category': 'X', 'kind': 'AREAS', 'count': 1 }                                | needs \"areas\", each with a list of courses",
            "{ 'category': 'X', 'kind': 'AREAS', 'count': 2, 'areas': { 'A': ['CMPT 310'] } } | count is 2 but only 1 areas are listed",
            "{ 'category': 'X', 'kind': 'AREAS', 'count': 1, 'areas': { 'A': ['CMPT 310'] }, 'required': ['CMPT 307'] } | required course CMPT 307 isn't in any area",
            "{ 'category': 'X', 'kind': 'AREAS', 'count': 1, 'areas': { 'A': ['CMPT 310'], 'B': ['CMPT 310'] } } | CMPT 310 is listed more than once",
            "{ 'category': 'X', 'kind': 'DESIGNATION', 'units': 6, 'designations': ['WQ'] }  | not a designation: WQ",
            "{ 'category': 'X', 'kind': 'DESIGNATION', 'units': 6 }                          | needs \"designations\"",
            "{ 'category': 'X', 'kind': 'DESIGNATION', 'unit': 6, 'designations': ['W'] }    | DESIGNATION doesn't use \"unit\"",
            "{ 'category': 'X', 'kind': 'DESIGNATION', 'units': 6, 'designations': ['W'], 'minGrade': 'C--' } | not a letter grade: C--",
            "{ 'category': 'X', 'kind': 'TOTAL_UNITS', 'units': -3 }                          | needs \"units\" above 0",
            "{ 'category': 'X', 'kind': 'NOTE', 'text': ' ' }                                 | needs \"text\"",
            "{ 'category': 'X', 'kind': 'NOTE', 'text': 'x', 'exclusive': 'yes' }             | \"exclusive\" needs to be true or false"
    })
    void mistakeInARequirementIsNamed(String requirement, String problem) {
        String json = setWith("{ \"category\": \"Totals\", \"kind\": \"TOTAL_UNITS\", \"units\": 120 }, "
                + requirement.replace('\'', '"'));

        assertThatThrownBy(() -> RequirementFile.fromJson(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Graduation requirements, Computing Science 2024, requirement 2 (")
                .hasMessageContaining(problem);
    }

    @Test
    void messageSaysWhichCategoryAndKind() {
        String json = setWith("""
                { "category": "Breadth", "kind": "AREAS", "count": 1 }
                """);

        assertThatThrownBy(() -> RequirementFile.fromJson(json))
                .hasMessage("Graduation requirements, Computing Science 2024, requirement 1 (Breadth, AREAS): "
                        + "needs \"areas\", each with a list of courses");
    }

    @Test
    void sameSetTwiceIsTurnedDown() {
        String set = """
                { "program": "COMPUTER_ENGINEERING", "entryYear": 2023,
                  "requirements": [ { "category": "Notes", "kind": "NOTE", "text": "x" } ] }
                """;

        assertThatThrownBy(() -> RequirementFile.fromJson("{ \"sets\": [" + set + "," + set + "] }"))
                .hasMessage("Graduation requirements, Computer Engineering 2023: listed more than once");
    }

    @Test
    void unknownProgramIsTurnedDown() {
        String json = """
                { "sets": [ { "program": "BIOLOGY", "entryYear": 2024,
                              "requirements": [ { "category": "Notes", "kind": "NOTE", "text": "x" } ] } ] }
                """;

        assertThatThrownBy(() -> RequirementFile.fromJson(json))
                .hasMessage("Graduation requirements, set 1: not a program: BIOLOGY");
    }

    @Test
    void setWithoutRequirementsIsTurnedDown() {
        String json = """
                { "sets": [ { "program": "COMPUTING_SCIENCE", "entryYear": 2024, "requirements": [] } ] }
                """;

        assertThatThrownBy(() -> RequirementFile.fromJson(json))
                .hasMessage("Graduation requirements, Computing Science 2024: needs a \"requirements\" list");
    }

    @Test
    void fileWithoutSetsIsTurnedDown() {
        assertThatThrownBy(() -> RequirementFile.fromJson("{ \"rules\": [] }"))
                .hasMessage("Graduation requirements: the file needs a \"sets\" list");
    }
}
