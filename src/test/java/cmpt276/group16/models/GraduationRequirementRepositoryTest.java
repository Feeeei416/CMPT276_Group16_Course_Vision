package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

// Covers the requirement_sets, requirements and requirement_courses tables together,
// since a set is only useful with its requirements and their courses
@DataJpaTest
class GraduationRequirementRepositoryTest {

    @Autowired
    private RequirementSetRepository setRepository;

    @Autowired
    private GraduationRequirementRepository requirementRepository;

    @Autowired
    private RequirementCourseRepository courseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private RequirementSet saveSet(Program program, int year) {
        return setRepository.saveAndFlush(new RequirementSet(program, year, "https://www.sfu.ca/students/calendar"));
    }

    private GraduationRequirement saveRequirement(RequirementSet set, int position, String category, RequirementKind kind) {
        return requirementRepository.saveAndFlush(new GraduationRequirement(set, position, category, kind));
    }

    private RequirementCourse saveCourse(GraduationRequirement requirement, String subject, String number,
                                         RequirementCourse.Use use) {
        return courseRepository.saveAndFlush(new RequirementCourse(requirement, subject, number, use));
    }

    @Test
    void setIsFoundByProgramAndEntryYear() {
        saveSet(Program.COMPUTING_SCIENCE, 2023);
        saveSet(Program.COMPUTING_SCIENCE, 2024);
        saveSet(Program.COMPUTER_ENGINEERING, 2024);
        entityManager.clear();

        RequirementSet found = setRepository.findByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2024).orElseThrow();

        assertThat(found.getProgram()).isEqualTo(Program.COMPUTING_SCIENCE);
        assertThat(found.getEntryYear()).isEqualTo(2024);
        assertThat(found.getShowName()).isEqualTo("Computing Science, entry 2024");
        assertThat(setRepository.existsByProgramAndEntryYear(Program.COMPUTER_ENGINEERING, 2024)).isTrue();
    }

    @Test
    void missingSetIsEmpty() {
        saveSet(Program.COMPUTING_SCIENCE, 2024);

        assertThat(setRepository.findByProgramAndEntryYear(Program.COMPUTER_ENGINEERING, 2024)).isEmpty();
        assertThat(setRepository.existsByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2023)).isFalse();
    }

    @Test
    void sameProgramAndYearCantBeSavedTwice() {
        saveSet(Program.COMPUTING_SCIENCE, 2024);

        assertThatThrownBy(() -> saveSet(Program.COMPUTING_SCIENCE, 2024))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void setsAreListedByProgramThenYear() {
        saveSet(Program.COMPUTER_ENGINEERING, 2024);
        saveSet(Program.COMPUTING_SCIENCE, 2024);
        saveSet(Program.COMPUTER_ENGINEERING, 2023);
        saveSet(Program.COMPUTING_SCIENCE, 2023);

        List<String> names = setRepository.findAllByOrderByProgramAscEntryYearAsc().stream()
                .map(RequirementSet::getShowName).toList();

        // Ordered by the enum's name, so COMPUTER_ENGINEERING comes before COMPUTING_SCIENCE
        assertThat(names).containsExactly("Computer Engineering, entry 2023", "Computer Engineering, entry 2024",
                "Computing Science, entry 2023", "Computing Science, entry 2024");
    }

    @Test
    void requirementsComeBackInPositionOrder() {
        RequirementSet set = saveSet(Program.COMPUTING_SCIENCE, 2024);
        saveRequirement(set, 2, "Upper division", RequirementKind.TOTAL_UNITS);
        saveRequirement(set, 0, "Lower division", RequirementKind.COURSE);
        saveRequirement(set, 1, "Lower division", RequirementKind.CHOOSE);
        entityManager.clear();

        List<RequirementKind> kinds = requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(set).stream()
                .map(GraduationRequirement::getKind).toList();

        assertThat(kinds).containsExactly(RequirementKind.COURSE, RequirementKind.CHOOSE, RequirementKind.TOTAL_UNITS);
    }

    @Test
    void requirementsOfAnotherSetArentReturned() {
        RequirementSet cs = saveSet(Program.COMPUTING_SCIENCE, 2024);
        RequirementSet ce = saveSet(Program.COMPUTER_ENGINEERING, 2024);
        saveRequirement(cs, 0, "Lower division", RequirementKind.COURSE);
        saveRequirement(ce, 0, "Required courses", RequirementKind.COURSE);
        saveRequirement(ce, 1, "Co-op", RequirementKind.CHOOSE);

        assertThat(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(cs)).hasSize(1);
        assertThat(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(ce)).hasSize(2);
    }

    @Test
    void requirementsAtTheSamePositionComeBackInTheOrderTheyWereSaved() {
        // Two rows can share a position partway through reordering them (#70), so the id breaks the tie
        RequirementSet set = saveSet(Program.COMPUTING_SCIENCE, 2024);
        saveRequirement(set, 1, "Upper division", RequirementKind.TOTAL_UNITS);
        saveRequirement(set, 0, "Lower division", RequirementKind.COURSE);
        saveRequirement(set, 0, "Lower division", RequirementKind.CHOOSE);
        entityManager.clear();

        List<RequirementKind> kinds = requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(set).stream()
                .map(GraduationRequirement::getKind).toList();

        assertThat(kinds).containsExactly(RequirementKind.COURSE, RequirementKind.CHOOSE, RequirementKind.TOTAL_UNITS);
    }

    @Test
    void newSetStartsAtFileVersionOne() {
        RequirementSet set = saveSet(Program.COMPUTING_SCIENCE, 2024);
        set.setFileVersion(3);
        setRepository.saveAndFlush(set);
        entityManager.clear();

        assertThat(setRepository.findByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2024).orElseThrow()
                .getFileVersion()).isEqualTo(3);
        assertThat(new RequirementSet(Program.COMPUTER_ENGINEERING, 2024, null).getFileVersion()).isEqualTo(1);
    }

    @Test
    void unitsFromRuleKeepsItsRangeAndLimit() {
        RequirementSet set = saveSet(Program.COMPUTER_ENGINEERING, 2024);
        GraduationRequirement esd = new GraduationRequirement(set, 0, "ESD electives", RequirementKind.UNITS_FROM);
        esd.setUnits(new BigDecimal("12"));
        esd.setSubject("ensc");
        esd.setMinNumber(300);
        esd.setLimitBelow(2, 400);
        esd.setExclusive(true);
        GraduationRequirement saved = requirementRepository.saveAndFlush(esd);
        entityManager.clear();

        GraduationRequirement found = requirementRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getUnits()).isEqualByComparingTo("12");
        assertThat(found.getSubject()).isEqualTo("ENSC");
        assertThat(found.getMinNumber()).isEqualTo(300);
        assertThat(found.getMaxCoursesBelow()).isEqualTo(2);
        assertThat(found.getBelowNumber()).isEqualTo(400);
        assertThat(found.isExclusive()).isTrue();
    }

    @Test
    void designationRuleKeepsEveryDesignation() {
        RequirementSet set = saveSet(Program.COMPUTER_ENGINEERING, 2024);
        GraduationRequirement humOrSoc = new GraduationRequirement(set, 0, "Complementary studies", RequirementKind.DESIGNATION);
        humOrSoc.setUnits(new BigDecimal("9"));
        humOrSoc.setDesignations(Set.of(Designation.B_HUM, Designation.B_SOC));
        humOrSoc.setMinGrade("C-");
        humOrSoc.setOutsideSubject("ensc");
        GraduationRequirement saved = requirementRepository.saveAndFlush(humOrSoc);
        entityManager.clear();

        GraduationRequirement found = requirementRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getDesignations()).containsExactlyInAnyOrder(Designation.B_HUM, Designation.B_SOC);
        assertThat(found.getMinGrade()).isEqualTo("C-");
        assertThat(found.getOutsideSubject()).isEqualTo("ENSC");
        assertThat(found.isExclusive()).isFalse();
    }

    @Test
    void noteKeepsItsText() {
        RequirementSet set = saveSet(Program.COMPUTING_SCIENCE, 2024);
        GraduationRequirement note = new GraduationRequirement(set, 0, "Totals and notes", RequirementKind.NOTE);
        note.setText("An upper-division GPA of 2.00 in program courses.");
        GraduationRequirement saved = requirementRepository.saveAndFlush(note);
        entityManager.clear();

        assertThat(requirementRepository.findById(saved.getId()).orElseThrow().getText())
                .isEqualTo("An upper-division GPA of 2.00 in program courses.");
    }

    @Test
    void requirementNeedsACategoryAndAPositionOfZeroOrMore() {
        RequirementSet set = saveSet(Program.COMPUTING_SCIENCE, 2024);

        assertThatThrownBy(() -> new GraduationRequirement(set, 0, " ", RequirementKind.COURSE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GraduationRequirement(set, -1, "Breadth", RequirementKind.AREAS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coursesAreSavedUppercaseWithTheirUseAndArea() {
        RequirementSet set = saveSet(Program.COMPUTING_SCIENCE, 2024);
        GraduationRequirement breadth = saveRequirement(set, 0, "Breadth", RequirementKind.AREAS);
        RequirementCourse required = new RequirementCourse(breadth, "cmpt", "307", RequirementCourse.Use.REQUIRED);
        required.setArea("Theoretical Computing Science");
        courseRepository.saveAndFlush(required);
        saveCourse(breadth, "CMPT", "310", RequirementCourse.Use.LISTED);
        entityManager.clear();

        List<RequirementCourse> courses = courseRepository.findByRequirementOrderByIdAsc(breadth);

        assertThat(courses).extracting(RequirementCourse::getCode).containsExactly("CMPT 307", "CMPT 310");
        assertThat(courses.get(0).getUse()).isEqualTo(RequirementCourse.Use.REQUIRED);
        assertThat(courses.get(0).getArea()).isEqualTo("Theoretical Computing Science");
        assertThat(courses.get(1).getArea()).isNull();
    }

    @Test
    void sameCourseCantBeListedTwiceInOneRequirement() {
        RequirementSet set = saveSet(Program.COMPUTING_SCIENCE, 2024);
        GraduationRequirement calculus = saveRequirement(set, 0, "Lower division", RequirementKind.CHOOSE);
        saveCourse(calculus, "MATH", "151", RequirementCourse.Use.LISTED);

        assertThatThrownBy(() -> saveCourse(calculus, "math", "151", RequirementCourse.Use.LISTED))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameCourseCanBeInTwoRequirements() {
        RequirementSet set = saveSet(Program.COMPUTING_SCIENCE, 2024);
        GraduationRequirement required = saveRequirement(set, 0, "BSc", RequirementKind.COURSE);
        GraduationRequirement tables = saveRequirement(set, 1, "BSc", RequirementKind.CHOOSE);
        saveCourse(required, "MACM", "316", RequirementCourse.Use.LISTED);
        saveCourse(tables, "MACM", "316", RequirementCourse.Use.LISTED);

        assertThat(courseRepository.findAll()).hasSize(2);
    }

    @Test
    void allCoursesOfASetComeBackInOneQuery() {
        RequirementSet cs = saveSet(Program.COMPUTING_SCIENCE, 2024);
        RequirementSet ce = saveSet(Program.COMPUTER_ENGINEERING, 2024);
        GraduationRequirement depth = saveRequirement(cs, 0, "Depth", RequirementKind.UNITS_FROM);
        GraduationRequirement calculus = saveRequirement(cs, 1, "Lower division", RequirementKind.CHOOSE);
        GraduationRequirement chem = saveRequirement(ce, 0, "Required courses", RequirementKind.COURSE);
        saveCourse(depth, "CMPT", "415", RequirementCourse.Use.EXCLUDED);
        saveCourse(calculus, "MATH", "150", RequirementCourse.Use.LISTED);
        saveCourse(chem, "CHEM", "121", RequirementCourse.Use.LISTED);
        entityManager.clear();

        List<String> codes = courseRepository.findByRequirementRequirementSetOrderByIdAsc(cs).stream()
                .map(RequirementCourse::getCode).toList();

        assertThat(codes).containsExactly("CMPT 415", "MATH 150");
    }
}
