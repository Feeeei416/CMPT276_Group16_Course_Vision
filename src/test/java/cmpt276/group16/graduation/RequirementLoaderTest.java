package cmpt276.group16.graduation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import cmpt276.group16.models.Designation;
import cmpt276.group16.models.GraduationRequirement;
import cmpt276.group16.models.GraduationRequirementRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementCourseRepository;
import cmpt276.group16.models.RequirementKind;
import cmpt276.group16.models.RequirementSet;
import cmpt276.group16.models.RequirementSetRepository;

@DataJpaTest
class RequirementLoaderTest {

    // Two small sets, written the same way as requirements.json
    private static final String FILE = """
            { "sets": [
              { "program": "COMPUTING_SCIENCE", "entryYear": 2024, "calendarUrl": "https://www.sfu.ca/cs-2024",
                "requirements": [
                  { "category": "Lower division", "kind": "COURSE", "courses": ["CMPT 120", "CMPT 125"] },
                  { "category": "Lower division", "kind": "CHOOSE", "title": "Calculus I", "count": 1,
                    "courses": ["MATH 150", "MATH 151"] },
                  { "category": "Breadth", "kind": "AREAS", "count": 2, "exclusive": true, "required": ["CMPT 307"],
                    "areas": { "Artificial Intelligence": ["CMPT 310"],
                               "Theoretical Computing Science": ["CMPT 307"] } },
                  { "category": "Depth", "kind": "UNITS_FROM", "units": 12, "exclusive": true,
                    "subject": "CMPT", "minNumber": 400, "excluded": ["CMPT 415"] },
                  { "category": "WQB", "kind": "DESIGNATION", "units": 6, "designations": ["W"], "minGrade": "C-" },
                  { "category": "Totals and notes", "kind": "TOTAL_UNITS", "units": 120 },
                  { "category": "Totals and notes", "kind": "NOTE", "text": "An upper-division GPA of 2.00." }
                ] },
              { "program": "COMPUTER_ENGINEERING", "entryYear": 2024,
                "requirements": [
                  { "category": "Required courses", "kind": "COURSE", "courses": ["ENSC 251"] }
                ] }
            ] }
            """;

    @Autowired
    private RequirementSetRepository setRepository;

    @Autowired
    private GraduationRequirementRepository requirementRepository;

    @Autowired
    private RequirementCourseRepository courseRepository;

    @Autowired
    private TestEntityManager entityManager;

    // Returns the names of the sets it added
    private List<String> load(String json) {
        return loadResult(json).added();
    }

    private RequirementLoader.LoadResult loadResult(String json) {
        RequirementLoader loader = new RequirementLoader(RequirementFile.fromJson(json), setRepository,
                requirementRepository, courseRepository);
        RequirementLoader.LoadResult result = loader.load();
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    // The Computing Science 2024 set from FILE with a different version, link and requirements
    private static String csVersion(int version, String note) {
        return """
                { "sets": [ { "program": "COMPUTING_SCIENCE", "entryYear": 2024, "version": %d,
                              "calendarUrl": "https://www.sfu.ca/new",
                              "requirements": [
                                { "category": "Notes", "kind": "NOTE", "text": "%s" },
                                { "category": "WQB", "kind": "DESIGNATION", "units": 6, "designations": ["B_SCI"],
                                  "outsideSubject": "CMPT" },
                                { "category": "Lower division", "kind": "COURSE", "courses": ["CMPT 201"] }
                              ] } ] }
                """.formatted(version, note);
    }

    private RequirementSet csSet() {
        return setRepository.findByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2024).orElseThrow();
    }

    @Test
    void addsEverySetInTheFile() {
        List<String> added = load(FILE);

        assertThat(added).containsExactly("Computing Science 2024", "Computer Engineering 2024");
        assertThat(setRepository.count()).isEqualTo(2);
        assertThat(csSet().getCalendarUrl()).isEqualTo("https://www.sfu.ca/cs-2024");
    }

    @Test
    void requirementsAreSavedInFileOrder() {
        load(FILE);

        List<GraduationRequirement> requirements = requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(csSet());

        // The COURSE entry with two courses became two requirements
        assertThat(requirements).extracting(GraduationRequirement::getKind).containsExactly(
                RequirementKind.COURSE, RequirementKind.COURSE, RequirementKind.CHOOSE, RequirementKind.AREAS,
                RequirementKind.UNITS_FROM, RequirementKind.DESIGNATION, RequirementKind.TOTAL_UNITS,
                RequirementKind.NOTE);
        assertThat(requirements).extracting(GraduationRequirement::getPosition).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
    }

    @Test
    void eachRequirementKeepsItsFields() {
        load(FILE);
        List<GraduationRequirement> requirements = requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(csSet());

        GraduationRequirement calculus = requirements.get(2);
        assertThat(calculus.getTitle()).isEqualTo("Calculus I");
        assertThat(calculus.getCourseCount()).isEqualTo(1);

        GraduationRequirement depth = requirements.get(4);
        assertThat(depth.getUnits()).isEqualByComparingTo("12");
        assertThat(depth.getSubject()).isEqualTo("CMPT");
        assertThat(depth.getMinNumber()).isEqualTo(400);
        assertThat(depth.isExclusive()).isTrue();

        GraduationRequirement writing = requirements.get(5);
        assertThat(writing.getDesignations()).containsExactly(Designation.W);
        assertThat(writing.getMinGrade()).isEqualTo("C-");

        assertThat(requirements.get(7).getText()).isEqualTo("An upper-division GPA of 2.00.");
    }

    @Test
    void coursesAreSavedWithTheirUseAndArea() {
        load(FILE);
        List<GraduationRequirement> requirements = requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(csSet());

        List<RequirementCourse> breadth = courseRepository.findByRequirementOrderByIdAsc(requirements.get(3));
        assertThat(breadth).extracting(RequirementCourse::getCode).containsExactly("CMPT 310", "CMPT 307");
        assertThat(breadth.get(1).getUse()).isEqualTo(RequirementCourse.Use.REQUIRED);
        assertThat(breadth.get(1).getArea()).isEqualTo("Theoretical Computing Science");

        List<RequirementCourse> depth = courseRepository.findByRequirementOrderByIdAsc(requirements.get(4));
        assertThat(depth).extracting(RequirementCourse::getCode).containsExactly("CMPT 415");
        assertThat(depth.get(0).getUse()).isEqualTo(RequirementCourse.Use.EXCLUDED);
    }

    @Test
    void loadingTwiceAddsNothingTheSecondTime() {
        load(FILE);
        long requirements = requirementRepository.count();

        List<String> added = load(FILE);

        assertThat(added).isEmpty();
        assertThat(setRepository.count()).isEqualTo(2);
        assertThat(requirementRepository.count()).isEqualTo(requirements);
    }

    @Test
    void savedSetIsLeftAloneWhenTheFileChangesWithoutANewVersion() {
        load(FILE);
        // An edit made in the app, like IT changing the Calendar link (#70)
        RequirementSet cs = csSet();
        cs.setCalendarUrl("https://www.sfu.ca/edited");
        setRepository.saveAndFlush(cs);
        long requirements = requirementRepository.count();

        // The same set in the file now has a different link and a different requirement
        load("""
                { "sets": [ { "program": "COMPUTING_SCIENCE", "entryYear": 2024, "calendarUrl": "https://www.sfu.ca/new",
                              "requirements": [ { "category": "Notes", "kind": "NOTE", "text": "Changed" } ] } ] }
                """);

        assertThat(csSet().getCalendarUrl()).isEqualTo("https://www.sfu.ca/edited");
        assertThat(requirementRepository.count()).isEqualTo(requirements);
    }

    @Test
    void newerVersionReplacesTheSavedSet() {
        load(FILE);
        long ceRequirements = requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(
                setRepository.findByProgramAndEntryYear(Program.COMPUTER_ENGINEERING, 2024).orElseThrow()).size();

        RequirementLoader.LoadResult result = loadResult(csVersion(2, "Fixed"));

        assertThat(result.added()).isEmpty();
        assertThat(result.replaced()).containsExactly("Computing Science 2024");
        RequirementSet cs = csSet();
        assertThat(cs.getFileVersion()).isEqualTo(2);
        assertThat(cs.getCalendarUrl()).isEqualTo("https://www.sfu.ca/new");
        List<GraduationRequirement> requirements = requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(cs);
        assertThat(requirements).extracting(GraduationRequirement::getKind)
                .containsExactly(RequirementKind.NOTE, RequirementKind.DESIGNATION, RequirementKind.COURSE);
        assertThat(requirements.get(0).getText()).isEqualTo("Fixed");
        assertThat(requirements.get(1).getOutsideSubject()).isEqualTo("CMPT");
        // The old courses are gone and only the new set's course is left
        assertThat(courseRepository.findByRequirementRequirementSetOrderByIdAsc(cs))
                .extracting(RequirementCourse::getCode).containsExactly("CMPT 201");
        // The set row is kept, and the other set isn't touched
        assertThat(setRepository.count()).isEqualTo(2);
        assertThat(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(
                setRepository.findByProgramAndEntryYear(Program.COMPUTER_ENGINEERING, 2024).orElseThrow()))
                .hasSize((int) ceRequirements);
    }

    @Test
    void sameOrOlderVersionLeavesTheSavedSetAlone() {
        loadResult(csVersion(3, "Version 3"));

        RequirementLoader.LoadResult same = loadResult(csVersion(3, "Changed without a new version"));
        RequirementLoader.LoadResult older = loadResult(csVersion(2, "Older"));

        assertThat(same.replaced()).isEmpty();
        assertThat(older.replaced()).isEmpty();
        assertThat(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(csSet()).get(0).getText())
                .isEqualTo("Version 3");
    }

    @Test
    void replacingTwiceInARowWorks() {
        load(FILE);
        loadResult(csVersion(2, "Second"));

        RequirementLoader.LoadResult result = loadResult(csVersion(3, "Third"));

        assertThat(result.replaced()).containsExactly("Computing Science 2024");
        assertThat(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(csSet())).hasSize(3);
        assertThat(courseRepository.findByRequirementRequirementSetOrderByIdAsc(csSet())).hasSize(1);
    }

    @Test
    void onlyTheMissingSetIsAdded() {
        setRepository.saveAndFlush(new RequirementSet(Program.COMPUTING_SCIENCE, 2024, null));

        List<String> added = load(FILE);

        assertThat(added).containsExactly("Computer Engineering 2024");
        // The set that was already there gets none of the file's requirements
        assertThat(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(csSet())).isEmpty();
    }

    @Test
    void emptyFileAddsNothing() {
        assertThat(load("{ \"sets\": [] }")).isEmpty();
        assertThat(setRepository.count()).isZero();
    }
}
