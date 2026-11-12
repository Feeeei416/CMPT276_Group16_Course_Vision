package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.math.BigDecimal;
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

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.PlanRepository;
import cmpt276.group16.models.PlanTermRepository;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.PlannedCourseRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.PlanService.TermPlan;

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
    private TermRepository termRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PlanService planService;

    private StudentProfile priya;
    private StudentProfile marcus;

    @BeforeEach
    void setUp() {
        planService = new PlanService(planRepository, planTermRepository, plannedCourseRepository, termRepository,
                courseRepository, studentProfileRepository, NOV_9_2026);
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");
        // The courses the tests plan; anything else isn't in the course list
        saveCourse("CMPT", "225", "3.00");
        saveCourse("CMPT", "276", "3.00");
        saveCourse("CMPT", "295", "3.00");
        saveCourse("MACM", "201", "3.00");
        saveCourse("PHYS", "140", "4.00");
        saveCourse("CMPT", "105W", "1.50");
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private void saveCourse(String subject, String number, String units) {
        courseRepository.saveAndFlush(new Course(subject, number, "Title of " + subject + " " + number,
                new BigDecimal(units), null, null, null));
    }

    // The student's plan as term name -> course codes, read back from the database
    private Map<String, List<String>> planOf(StudentProfile student) {
        entityManager.flush();
        entityManager.clear();
        Map<String, List<String>> plan = new LinkedHashMap<>();
        planService.termPlans(student).forEach(termPlan -> plan.put(termPlan.term().getName(),
                termPlan.courses().stream().map(PlannedCourse::getCode).toList()));
        return plan;
    }

    private TermPlan termPlan(StudentProfile student, String termName) {
        return planService.termPlans(student).stream()
                .filter(termPlan -> termPlan.term().getName().equals(termName))
                .findFirst().orElseThrow();
    }

    private Long termId(StudentProfile student, String termName) {
        return termPlan(student, termName).term().getId();
    }

    private Long courseId(StudentProfile student, String code) {
        return planService.termPlans(student).stream()
                .flatMap(termPlan -> termPlan.courses().stream())
                .filter(course -> course.getCode().equals(code))
                .map(PlannedCourse::getId)
                .findFirst().orElseThrow();
    }

    // -- Terms --

    // Opening My plan only reads, so a student who hasn't added anything has no plan saved
    @Test
    void studentHasNoPlanUntilTheyAddATerm() {
        assertThat(planService.termPlans(priya)).isEmpty();
        assertThat(planRepository.findByStudentAndMainTrue(priya)).isEmpty();
    }

    @Test
    void firstTermCreatesTheMainPlan() {
        assertThat(planService.addTerm(priya, 2027, "FALL")).isEmpty();

        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of()));
        assertThat(planRepository.findByStudentAndMainTrue(priya).orElseThrow().getName()).isEqualTo("My plan");
    }

    @Test
    void moreTermsGoInTheSamePlanOldestFirst() {
        planService.addTerm(priya, 2028, "SPRING");
        planService.addTerm(priya, 2027, "SUMMER");
        planService.addTerm(priya, 2027, "FALL");

        assertThat(planOf(priya).keySet()).containsExactly("2027 Summer", "2027 Fall", "2028 Spring");
        assertThat(planRepository.count()).isEqualTo(1);
    }

    // The import may not have reached a term yet, so adding it to a plan creates the shared row.
    // A second student planning the same term reuses that row
    @Test
    void plansShareOneRowPerTerm() {
        assertThat(termRepository.findByTermYearAndSeason(2028, Season.FALL)).isEmpty();

        planService.addTerm(priya, 2028, "FALL");
        planService.addTerm(marcus, 2028, "FALL");

        assertThat(termRepository.findAll())
            .filteredOn(term -> term.getTermYear() == 2028 && term.getSeason() == Season.FALL)
            .hasSize(1);
        assertThat(planOf(marcus).keySet()).containsExactly("2028 Fall");
    }

    // The rules are tested in PlanRulesTest; this checks the service uses them and saves nothing on an error
    @ParameterizedTest
    @CsvSource({
        "2027, SPRING, Choose a term after next semester (2027 Spring).",
        "2034, SPRING, Choose a term no later than 2033 Fall.",
        "2027, WINTER, 'Choose Spring, Summer or Fall.'"
    })
    void rejectsTermsTheRulesDontAllow(int year, String season, String error) {
        assertThat(planService.addTerm(priya, year, season)).containsExactly(error);

        assertThat(planRepository.count()).isZero();
        assertThat(planTermRepository.count()).isZero();
    }

    @Test
    void rejectsATermAlreadyInThePlan() {
        planService.addTerm(priya, 2027, "FALL");

        assertThat(planService.addTerm(priya, 2027, "FALL")).containsExactly("2027 Fall is already in your plan.");
        assertThat(planOf(priya).keySet()).containsExactly("2027 Fall");
    }

    // Each student gets their own plan, so the same term can be in both
    @Test
    void twoStudentsCanPlanTheSameTerm() {
        planService.addTerm(priya, 2027, "FALL");

        assertThat(planService.addTerm(marcus, 2027, "FALL")).isEmpty();
        assertThat(planOf(marcus).keySet()).containsExactly("2027 Fall");
    }

    @Test
    void removingATermRemovesItsCourses() {
        planService.addTerm(priya, 2027, "SUMMER");
        planService.addTerm(priya, 2027, "FALL");
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
        planService.addTerm(priya, 2027, "FALL");

        assertThat(planService.removeTerm(marcus, termId(priya, "2027 Fall")))
            .containsExactly("That term isn't in your plan.");
        assertThat(planOf(priya).keySet()).containsExactly("2027 Fall");
    }

    // -- Courses --

    @Test
    void addsCoursesInTheOrderAdded() {
        planService.addTerm(priya, 2027, "FALL");
        Long fall = termId(priya, "2027 Fall");

        assertThat(planService.addCourse(priya, fall, " cmpt276 ")).isEmpty();
        assertThat(planService.addCourse(priya, fall, "MACM 201")).isEmpty();

        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of("CMPT 276", "MACM 201")));
    }

    @Test
    void rejectsTextThatIsntACourseCode() {
        planService.addTerm(priya, 2027, "FALL");

        assertThat(planService.addCourse(priya, termId(priya, "2027 Fall"), "data structures"))
            .containsExactly("Enter a course code like CMPT 225.");
        assertThat(plannedCourseRepository.count()).isZero();
    }

    // The code looks right, but SFU's course data (imported into courses) doesn't have it
    @Test
    void rejectsACodeThatIsntInTheCourseList() {
        planService.addTerm(priya, 2027, "FALL");

        assertThat(planService.addCourse(priya, termId(priya, "2027 Fall"), "cmpt 999"))
            .containsExactly("CMPT 999 isn't in the course list.");
        assertThat(plannedCourseRepository.count()).isZero();
    }

    // A course only needs to be taken once, so it can't be in two terms of the same plan
    @Test
    void rejectsACourseAlreadyInAnotherTerm() {
        planService.addTerm(priya, 2027, "SUMMER");
        planService.addTerm(priya, 2027, "FALL");
        planService.addCourse(priya, termId(priya, "2027 Summer"), "CMPT 225");

        assertThat(planService.addCourse(priya, termId(priya, "2027 Fall"), "cmpt 225"))
            .containsExactly("CMPT 225 is already planned for 2027 Summer. "
                    + "To move it to 2027 Fall, remove it from 2027 Summer first.");
        assertThat(planOf(priya)).containsExactly(
            entry("2027 Summer", List.of("CMPT 225")),
            entry("2027 Fall", List.of()));
    }

    @Test
    void rejectsACourseAlreadyInTheSameTerm() {
        planService.addTerm(priya, 2027, "FALL");
        Long fall = termId(priya, "2027 Fall");
        planService.addCourse(priya, fall, "CMPT 225");

        assertThat(planService.addCourse(priya, fall, "CMPT 225"))
            .containsExactly("CMPT 225 is already in 2027 Fall.");
        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of("CMPT 225")));
    }

    @Test
    void cantAddACourseToAnotherStudentsTerm() {
        planService.addTerm(priya, 2027, "FALL");

        assertThat(planService.addCourse(marcus, termId(priya, "2027 Fall"), "CMPT 225"))
            .containsExactly("That term isn't in your plan.");
        assertThat(plannedCourseRepository.count()).isZero();
    }

    @Test
    void removesACourse() {
        planService.addTerm(priya, 2027, "FALL");
        Long fall = termId(priya, "2027 Fall");
        planService.addCourse(priya, fall, "CMPT 276");
        planService.addCourse(priya, fall, "CMPT 295");

        assertThat(planService.removeCourse(priya, courseId(priya, "CMPT 276"))).isEmpty();

        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of("CMPT 295")));
    }

    @Test
    void cantRemoveAnotherStudentsCourse() {
        planService.addTerm(priya, 2027, "FALL");
        planService.addCourse(priya, termId(priya, "2027 Fall"), "CMPT 276");

        assertThat(planService.removeCourse(marcus, courseId(priya, "CMPT 276")))
            .containsExactly("That course isn't in your plan.");
        assertThat(planOf(priya)).containsExactly(entry("2027 Fall", List.of("CMPT 276")));
    }

    // -- Units --

    // Units come from the courses table: 3 + 4 + 1.5
    @Test
    void addsUpEachTermsUnits() {
        planService.addTerm(priya, 2027, "SUMMER");
        planService.addTerm(priya, 2027, "FALL");
        Long fall = termId(priya, "2027 Fall");
        planService.addCourse(priya, fall, "CMPT 225");
        planService.addCourse(priya, fall, "PHYS 140");
        planService.addCourse(priya, fall, "CMPT 105W");

        assertThat(termPlan(priya, "2027 Fall").unitsText()).isEqualTo("8.5 units");
        assertThat(termPlan(priya, "2027 Summer").unitsText()).isEqualTo("0 units");
    }

    // -- Page values --

    @Test
    void usesTheClockForNextSemesterAndYears() {
        assertThat(planService.nextSemester()).isEqualTo("2027 Spring");
        assertThat(planService.plannableYears()).startsWith(2027).endsWith(2033);
    }
}
