package cmpt276.group16.graduation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import cmpt276.group16.graduation.RequirementFile.RuleEntry;
import cmpt276.group16.models.GraduationRequirement;
import cmpt276.group16.models.GraduationRequirementRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementCourseRepository;
import cmpt276.group16.models.RequirementKind;
import cmpt276.group16.models.RequirementSet;
import cmpt276.group16.models.RequirementSetRepository;

// Uses the test database (H2), with a set saved by the loader the same way it saves requirements.json
@DataJpaTest
class RequirementEditorTest {
    // One requirement of each kind, written the way requirements.json writes them
    private static final String FILE = """
            { "sets": [
              { "program": "COMPUTING_SCIENCE", "entryYear": 2024,
                "requirements": [
                  { "category": "Lower division", "kind": "COURSE", "courses": ["CMPT 120"] },
                  { "category": "Lower division", "kind": "CHOOSE", "title": "Calculus I", "count": 1,
                    "courses": ["MATH 150", "MATH 151"], "minGrade": "C-" },
                  { "category": "Breadth", "kind": "AREAS", "count": 2, "exclusive": true, "required": ["CMPT 307"],
                    "areas": { "Artificial Intelligence": ["CMPT 310", "CMPT 410"],
                               "Theoretical Computing Science": ["CMPT 307"] } },
                  { "category": "Depth", "kind": "UNITS_FROM", "title": "CMPT 400 and above", "units": 12,
                    "subject": "CMPT", "minNumber": 400, "excluded": ["CMPT 415"],
                    "maxCoursesBelow": 1, "belowNumber": 400 },
                  { "category": "Electives", "kind": "UNITS_FROM", "units": 6, "courses": ["MACM 201", "MATH 232"] },
                  { "category": "WQB", "kind": "DESIGNATION", "units": 6, "designations": ["B_HUM", "B_SOC"],
                    "outsideSubject": "CMPT" },
                  { "category": "Totals and notes", "kind": "TOTAL_UNITS", "units": 44, "minNumber": 300 },
                  { "category": "Totals and notes", "kind": "NOTE", "text": "An upper-division GPA of 2.00." }
                ] } ] }
            """;

    private static final String NOTE = """
            { "category": "Totals and notes", "kind": "NOTE", "text": "Added on the page." }
            """;

    // count is more than the courses listed, which the file's checks turn down
    private static final String MISTAKE = """
            { "category": "Lower division", "kind": "CHOOSE", "count": 3, "courses": ["MATH 150", "MATH 151"] }
            """;

    @Autowired
    private RequirementSetRepository setRepository;

    @Autowired
    private GraduationRequirementRepository requirementRepository;

    @Autowired
    private RequirementCourseRepository courseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private RequirementEditor editor;

    @BeforeEach
    void setUp() {
        RequirementLoader loader = new RequirementLoader(RequirementFile.fromJson(FILE), setRepository,
                requirementRepository, courseRepository);
        loader.load();
        editor = new RequirementEditor(setRepository, requirementRepository, courseRepository, loader);
        reload();
    }

    // Writes the changes and forgets the loaded objects, so the checks read what's really saved
    private void reload() {
        entityManager.flush();
        entityManager.clear();
    }

    private RequirementSet cs() {
        return setRepository.findByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2024).orElseThrow();
    }

    private List<GraduationRequirement> requirements() {
        return requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(cs());
    }

    private List<String> codes(GraduationRequirement requirement) {
        return courseRepository.findByRequirementOrderByIdAsc(requirement).stream()
                .map(RequirementCourse::getCode).toList();
    }

    // -- Showing a requirement as JSON --

    // Opening a requirement and saving it unchanged mustn't change it: its JSON parses back to the same rule.
    // Covers every kind, and the optional fields (title, minGrade, exclusive, ranges, excluded, outsideSubject)
    @Test
    void jsonParsesBackToTheSameRule() {
        List<RuleEntry> fromFile = RequirementFile.fromJson(FILE).sets().get(0).requirements();
        List<GraduationRequirement> saved = requirements();
        assertThat(saved).hasSameSizeAs(fromFile);

        for (int i = 0; i < saved.size(); i++) {
            List<RuleEntry> parsed = RequirementFile.parseRequirement(editor.json(saved.get(i).getId()));
            assertThat(parsed).hasSize(1);
            assertThat(parsed.get(0))
                    .as("requirement %d (%s)", i, saved.get(i).getKind())
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(fromFile.get(i));
        }
    }

    // The page lists every requirement in order, each with its JSON
    @Test
    void rowsListEveryRequirementWithItsJson() {
        List<RequirementEditor.Row> rows = editor.rows(cs());

        assertThat(rows).hasSize(8);
        assertThat(rows.get(0).json()).contains("\"CMPT 120\"");
        assertThat(rows.get(7).json()).contains("An upper-division GPA of 2.00.");
    }

    // -- Adding --

    @Test
    void addPutsTheRequirementLastAndMarksTheSetEdited() {
        editor.add(cs().getId(), NOTE);
        reload();

        List<GraduationRequirement> requirements = requirements();
        assertThat(requirements).hasSize(9);
        GraduationRequirement added = requirements.get(8);
        assertThat(added.getText()).isEqualTo("Added on the page.");
        assertThat(added.getPosition()).isEqualTo(requirements.get(7).getPosition() + 1);
        assertThat(cs().isEditedInApp()).isTrue();
    }

    // The JSON is checked before anything is saved, so a mistake changes nothing
    @Test
    void addWithAMistakeSavesNothing() {
        assertThatThrownBy(() -> editor.add(cs().getId(), MISTAKE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count is 3 but only 2 courses are listed");
        reload();

        assertThat(requirements()).hasSize(8);
        assertThat(cs().isEditedInApp()).isFalse();
    }

    @Test
    void addToAnEmptySetStartsAtTheTop() {
        RequirementSet empty = setRepository.save(new RequirementSet(Program.COMPUTER_ENGINEERING, 2024, null));
        reload();

        editor.add(empty.getId(), NOTE);
        reload();

        assertThat(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(empty))
                .extracting(GraduationRequirement::getPosition).containsExactly(0);
    }

    // -- Changing --

    // The requirement is replaced in the same place, and its old courses go with it
    @Test
    void updateReplacesTheRequirementInTheSamePlace() {
        GraduationRequirement calculus = requirements().get(1);
        int position = calculus.getPosition();
        long courses = courseRepository.count();

        editor.update(calculus.getId(), """
                { "category": "Lower division", "kind": "CHOOSE", "title": "Calculus", "count": 1,
                  "courses": ["MATH 150", "MATH 151", "MATH 154"] }
                """);
        reload();

        List<GraduationRequirement> requirements = requirements();
        assertThat(requirements).hasSize(8);
        GraduationRequirement changed = requirements.get(1);
        assertThat(changed.getTitle()).isEqualTo("Calculus");
        assertThat(changed.getPosition()).isEqualTo(position);
        assertThat(changed.getMinGrade()).isNull();
        assertThat(codes(changed)).containsExactly("MATH 150", "MATH 151", "MATH 154");
        // Two courses before, three after
        assertThat(courseRepository.count()).isEqualTo(courses + 1);
        assertThat(cs().isEditedInApp()).isTrue();
    }

    // A mistake leaves the requirement as it was
    @Test
    void updateWithAMistakeChangesNothing() {
        GraduationRequirement calculus = requirements().get(1);

        assertThatThrownBy(() -> editor.update(calculus.getId(), MISTAKE))
                .isInstanceOf(IllegalArgumentException.class);
        reload();

        GraduationRequirement after = requirementRepository.findById(calculus.getId()).orElseThrow();
        assertThat(after.getTitle()).isEqualTo("Calculus I");
        assertThat(codes(after)).containsExactly("MATH 150", "MATH 151");
        assertThat(cs().isEditedInApp()).isFalse();
    }

    // A COURSE entry with two courses becomes two requirements, both where the old one was
    @Test
    void updateToTwoCoursesKeepsBothInThatPlace() {
        GraduationRequirement first = requirements().get(0);
        int position = first.getPosition();

        editor.update(first.getId(), """
                { "category": "Lower division", "kind": "COURSE", "courses": ["CMPT 120", "CMPT 125"] }
                """);
        reload();

        List<GraduationRequirement> requirements = requirements();
        assertThat(requirements).hasSize(9);
        assertThat(requirements.subList(0, 2)).extracting(GraduationRequirement::getPosition)
                .containsExactly(position, position);
        assertThat(requirements.subList(0, 2)).extracting(requirement -> codes(requirement).get(0))
                .containsExactly("CMPT 120", "CMPT 125");
    }

    // -- Removing --

    @Test
    void removeDeletesTheRequirementAndItsCoursesOnly() {
        GraduationRequirement calculus = requirements().get(1);
        long courses = courseRepository.count();

        editor.remove(calculus.getId());
        reload();

        assertThat(requirements()).hasSize(7);
        assertThat(requirementRepository.findById(calculus.getId())).isEmpty();
        assertThat(courseRepository.count()).isEqualTo(courses - 2);
        assertThat(requirements()).extracting(GraduationRequirement::getKind).doesNotContain(RequirementKind.CHOOSE);
        assertThat(cs().isEditedInApp()).isTrue();
    }

    // -- A requirement that isn't there --

    // The page turns this into a 404
    @Test
    void unknownRequirementIsNotFound() {
        assertThatThrownBy(() -> editor.json(999_999L)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> editor.update(999_999L, NOTE)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> editor.remove(999_999L)).isInstanceOf(NoSuchElementException.class);
    }
}
