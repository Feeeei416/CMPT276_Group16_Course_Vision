package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import cmpt276.group16.models.PlanRepository;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlanTermRepository;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.PlannedCourseRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// @DataJpaTest only loads repositories, so the service is built by hand with a fixed date
@DataJpaTest
class PlanServiceTest {

    // 9 November 2026, so next semester is 2027 Spring and the first plannable term is 2027 Summer
    private static final Clock NOV_9_2026 =
            Clock.fixed(Instant.parse("2026-11-09T20:00:00Z"), ZoneId.of("America/Vancouver"));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanTermRepository planTermRepository;

    @Autowired
    private PlannedCourseRepository plannedCourseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PlanService planService;

    private StudentProfile priya;
    private StudentProfile marcus;

    @BeforeEach
    void setUp() {
        planService = new PlanService(planRepository, planTermRepository, plannedCourseRepository, NOV_9_2026);
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    // The student's plan as term name -> course codes, read back from the database
    private Map<String, List<String>> planOf(StudentProfile student) {
        entityManager.flush();
        entityManager.clear();
        Map<String, List<String>> plan = new LinkedHashMap<>();
        planService.coursesByTerm(student).forEach((term, courses) ->
                plan.put(term.getName(), courses.stream().map(PlannedCourse::getCode).toList()));
        return plan;
    }

    private Long termId(StudentProfile student, String termName) {
        return planService.coursesByTerm(student).keySet().stream()
                .filter(term -> term.getName().equals(termName))
                .map(PlanTerm::getId)
                .findFirst().orElseThrow();
    }

    private Long courseId(StudentProfile student, String code) {
        return planService.coursesByTerm(student).values().stream()
                .flatMap(List::stream)
                .filter(course -> course.getCode().equals(code))
                .map(PlannedCourse::getId)
                .findFirst().orElseThrow();
    }

    // -- Terms --

    // Opening My plan only reads, so a student who hasn't added anything has no plan saved
    @Test
    void studentHasNoPlanUntilTheyAddATerm() {
        assertThat(planService.coursesByTerm(priya)).isEmpty();
        assertThat(planRepository.findByStudentAndMainTrue(priya)).isEmpty();
    }

    @Test
    void firstTermCreatesTheMainPlan() {
        assertThat(planService.addTerm(priya, 2027, "Fall")).isEmpty();

        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of()));
        assertThat(planRepository.findByStudentAndMainTrue(priya).orElseThrow().getName()).isEqualTo("My plan");
    }

    @Test
    void moreTermsGoInTheSamePlanOldestFirst() {
        planService.addTerm(priya, 2028, "Spring");
        planService.addTerm(priya, 2027, "Summer");
        planService.addTerm(priya, 2027, "Fall");

        assertThat(planOf(priya).keySet()).containsExactly("2027 Summer", "2027 Fall", "2028 Spring");
        assertThat(planRepository.count()).isEqualTo(1);
    }

    // The rules are tested in PlanRulesTest; this checks the service uses them and saves nothing on an error
    @ParameterizedTest
    @CsvSource({
        "2027, Spring, Choose a term after next semester (2027 Spring).",
        "2034, Spring, Choose a term no later than 2033 Fall.",
        "2027, Winter, 'Choose Spring, Summer or Fall.'"
    })
    void rejectsTermsTheRulesDontAllow(int year, String season, String error) {
        assertThat(planService.addTerm(priya, year, season)).containsExactly(error);

        assertThat(planRepository.count()).isZero();
        assertThat(planTermRepository.count()).isZero();
    }

    @Test
    void rejectsATermAlreadyInThePlan() {
        planService.addTerm(priya, 2027, "Fall");

        assertThat(planService.addTerm(priya, 2027, "Fall")).containsExactly("2027 Fall is already in your plan.");
        assertThat(planOf(priya).keySet()).containsExactly("2027 Fall");
    }

    // Each student gets their own plan, so the same term can be in both
    @Test
    void twoStudentsCanPlanTheSameTerm() {
        planService.addTerm(priya, 2027, "Fall");

        assertThat(planService.addTerm(marcus, 2027, "Fall")).isEmpty();
        assertThat(planOf(marcus).keySet()).containsExactly("2027 Fall");
    }

    @Test
    void removingATermRemovesItsCourses() {
        planService.addTerm(priya, 2027, "Summer");
        planService.addTerm(priya, 2027, "Fall");
        planService.addCourse(priya, termId(priya, "2027 Summer"), "CMPT 225");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 295");

        assertThat(planService.removeTerm(priya, termId(priya, "2027 Fall"))).isEmpty();

        assertThat(planOf(priya)).containsExactly(entry("2027 Summer", List.of("CMPT 225")));
        assertThat(plannedCourseRepository.count()).isEqualTo(1);
    }

    // Changing the term id in a request can't remove someone else's term
    @Test
    void cantRemoveAnotherStudentsTerm() {
        planService.addTerm(priya, 2027, "Fall");

        assertThat(planService.removeTerm(marcus, termId(priya, "2027 Fall")))
            .containsExactly("That term isn't in your plan.");
        assertThat(planOf(priya).keySet()).containsExactly("2027 Fall");
    }

    // -- Courses --

    @Test
    void addsCoursesInCapitalsInTheOrderAdded() {
        planService.addTerm(priya, 2027, "Fall");
        Long fall = termId(priya, "2027 Fall");

        assertThat(planService.addCourse(priya, fall, " cmpt276 ")).isEmpty();
        assertThat(planService.addCourse(priya, fall, "MACM 201")).isEmpty();

        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of("CMPT 276", "MACM 201")));
    }

    @Test
    void rejectsTextThatIsntACourseCode() {
        planService.addTerm(priya, 2027, "Fall");

        assertThat(planService.addCourse(priya, termId(priya, "2027 Fall"), "data structures"))
            .containsExactly("Enter a course code like CMPT 225.");
        assertThat(plannedCourseRepository.count()).isZero();
    }

    // A course only needs to be taken once, so it can't be in two terms of the same plan
    @Test
    void rejectsACourseAlreadyInAnotherTerm() {
        planService.addTerm(priya, 2027, "Summer");
        planService.addTerm(priya, 2027, "Fall");
        planService.addCourse(priya, termId(priya, "2027 Summer"), "CMPT 225");

        assertThat(planService.addCourse(priya, termId(priya, "2027 Fall"), "cmpt 225"))
            .containsExactly("CMPT 225 is already in your plan.");
        assertThat(planOf(priya)).containsExactly(
            entry("2027 Summer", List.of("CMPT 225")),
            entry("2027 Fall", List.of()));
    }

    @Test
    void cantAddACourseToAnotherStudentsTerm() {
        planService.addTerm(priya, 2027, "Fall");

        assertThat(planService.addCourse(marcus, termId(priya, "2027 Fall"), "CMPT 225"))
            .containsExactly("That term isn't in your plan.");
        assertThat(plannedCourseRepository.count()).isZero();
    }

    @Test
    void removesACourse() {
        planService.addTerm(priya, 2027, "Fall");
        Long fall = termId(priya, "2027 Fall");
        planService.addCourse(priya, fall, "CMPT 276");
        planService.addCourse(priya, fall, "CMPT 295");

        assertThat(planService.removeCourse(priya, courseId(priya, "CMPT 276"))).isEmpty();

        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of("CMPT 295")));
    }

    @Test
    void cantRemoveAnotherStudentsCourse() {
        planService.addTerm(priya, 2027, "Fall");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");

        assertThat(planService.removeCourse(marcus, courseId(priya, "CMPT 276")))
            .containsExactly("That course isn't in your plan.");
        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of("CMPT 276")));
    }

    // -- Page values --

    @Test
    void usesTheClockForNextSemesterAndYears() {
        assertThat(planService.nextSemester()).isEqualTo("2027 Spring");
        assertThat(planService.plannableYears()).startsWith(2027).endsWith(2033);
    }
}
