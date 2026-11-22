package cmpt276.group16.graduation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.core.io.ClassPathResource;

import cmpt276.group16.graduation.GraduationRequirements.RequirementWithCourses;
import cmpt276.group16.graduation.GraduationRequirements.StudentRequirements;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementCourseRepository;
import cmpt276.group16.models.RequirementKind;
import cmpt276.group16.models.RequirementRepository;
import cmpt276.group16.models.RequirementSetRepository;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// Uses the real requirements file, so these are the requirements the Graduation Report will show
@DataJpaTest
class GraduationRequirementsTest {

    @Autowired
    private RequirementSetRepository setRepository;

    @Autowired
    private RequirementRepository requirementRepository;

    @Autowired
    private RequirementCourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TestEntityManager entityManager;

    private GraduationRequirements graduationRequirements;

    @BeforeEach
    void makeService() {
        graduationRequirements = new GraduationRequirements(setRepository, requirementRepository, courseRepository);
    }

    // Saves the four real sets, the same as startup does
    private void loadRealRequirements() {
        RequirementFile file = new RequirementFile(new ClassPathResource("requirements/requirements.json"));
        new RequirementLoader(file, setRepository, requirementRepository, courseRepository).loadMissingSets();
        entityManager.flush();
        entityManager.clear();
    }

    private StudentProfile student(String email, Program program, int year) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, program, year));
    }

    private static List<String> codes(RequirementWithCourses item) {
        return item.courses().stream().map(RequirementCourse::getCode).toList();
    }

    @Test
    void studentGetsTheSetForTheirProgramAndYear() {
        loadRealRequirements();
        StudentRequirements cs = graduationRequirements
                .forStudent(student("cs@example.com", Program.COMPUTING_SCIENCE, 2024)).orElseThrow();
        StudentRequirements ce = graduationRequirements
                .forStudent(student("ce@example.com", Program.COMPUTER_ENGINEERING, 2023)).orElseThrow();

        assertThat(cs.set().getShowName()).isEqualTo("Computing Science, entry 2024");
        assertThat(ce.set().getShowName()).isEqualTo("Computer Engineering, entry 2023");
    }

    @Test
    void requirementsComeInFileOrder() {
        loadRealRequirements();
        StudentRequirements cs = graduationRequirements
                .forStudent(student("cs@example.com", Program.COMPUTING_SCIENCE, 2024)).orElseThrow();

        List<Integer> positions = cs.requirements().stream().map(item -> item.requirement().getPosition()).toList();
        assertThat(positions).isSorted();
        assertThat(codes(cs.requirements().get(0))).containsExactly("CMPT 105W");
    }

    @Test
    void eachRequirementHasItsOwnCourses() {
        loadRealRequirements();
        StudentRequirements cs = graduationRequirements
                .forStudent(student("cs@example.com", Program.COMPUTING_SCIENCE, 2024)).orElseThrow();

        RequirementWithCourses calculusOne = cs.requirements().stream()
                .filter(item -> "Calculus I".equals(item.requirement().getTitle())).findFirst().orElseThrow();
        assertThat(codes(calculusOne)).containsExactly("MATH 150", "MATH 151", "MATH 154", "MATH 157");

        RequirementWithCourses depth = cs.requirements().stream()
                .filter(item -> item.requirement().getCategory().equals("Depth")).findFirst().orElseThrow();
        assertThat(codes(depth)).containsExactly("CMPT 415", "CMPT 416", "CMPT 498");
        assertThat(depth.courses()).extracting(RequirementCourse::getUse).containsOnly(RequirementCourse.Use.EXCLUDED);
    }

    @Test
    void requirementsWithoutCoursesHaveAnEmptyList() {
        loadRealRequirements();
        StudentRequirements cs = graduationRequirements
                .forStudent(student("cs@example.com", Program.COMPUTING_SCIENCE, 2024)).orElseThrow();

        assertThat(cs.requirements())
                .filteredOn(item -> item.requirement().getKind() == RequirementKind.NOTE
                        || item.requirement().getKind() == RequirementKind.DESIGNATION)
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.courses()).isEmpty());
    }

    @Test
    void requirementsAreGroupedByCategoryInCalendarOrder() {
        loadRealRequirements();
        StudentRequirements cs = graduationRequirements
                .forStudent(student("cs@example.com", Program.COMPUTING_SCIENCE, 2024)).orElseThrow();

        assertThat(cs.byCategory().keySet()).containsExactly(
                "Lower division", "Upper division", "Breadth", "Depth", "BSc", "WQB", "Totals and notes");
        assertThat(cs.byCategory().get("WQB")).hasSize(5);
    }

    @Test
    void studentWithoutASetGetsNothing() {
        // Nothing loaded, as if the student's program and year had no requirements yet
        StudentProfile student = student("cs@example.com", Program.COMPUTING_SCIENCE, 2024);

        assertThat(graduationRequirements.forStudent(student)).isEmpty();
    }
}
