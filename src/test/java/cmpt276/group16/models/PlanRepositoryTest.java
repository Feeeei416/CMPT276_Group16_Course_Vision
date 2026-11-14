package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

// Covers the plans, plan_terms and planned_courses tables together, since a plan is only useful with all three
@DataJpaTest
class PlanRepositoryTest {

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

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private Plan saveMainPlan(StudentProfile student) {
        return planRepository.saveAndFlush(new Plan(student, "My plan", true));
    }

    // The shared row in terms, reused when it already exists
    private Term term(int year, Season season) {
        return termRepository.findByTermYearAndSeason(year, season)
                .orElseGet(() -> termRepository.saveAndFlush(new Term(year, season)));
    }

    // A course in the courses table, reused when it already exists
    private Course course(String subject, String number) {
        return courseRepository.findBySubjectAndNumber(subject, number)
                .orElseGet(() -> courseRepository.saveAndFlush(new Course(subject, number,
                        "Title of " + subject + " " + number, new BigDecimal("3.00"), null, null, null)));
    }

    private PlanTerm saveTerm(Plan plan, int year, Season season) {
        return planTermRepository.saveAndFlush(new PlanTerm(plan, term(year, season)));
    }

    private PlannedCourse saveCourse(PlanTerm term, String subject, String number) {
        return plannedCourseRepository.saveAndFlush(new PlannedCourse(term, course(subject, number)));
    }

    @Test
    void planWithTermsAndCoursesIsSavedAndFound() {
        StudentProfile student = saveStudent("priya@example.com");
        Plan plan = saveMainPlan(student);
        PlanTerm fall = saveTerm(plan, 2027, Season.FALL);
        saveCourse(fall, "CMPT", "225");
        saveCourse(fall, "MACM", "201");
        entityManager.clear();

        Plan found = planRepository.findByStudentAndMainTrue(student).orElseThrow();
        assertThat(found.getName()).isEqualTo("My plan");
        assertThat(planTermRepository.findByPlan(found))
            .extracting(PlanTerm::getName)
            .containsExactly("2027 Fall");
        assertThat(plannedCourseRepository.findByTermPlan(found))
            .extracting(PlannedCourse::getCode)
            .containsExactlyInAnyOrder("CMPT 225", "MACM 201");
    }

    // A planned course reads its code, title and units from the courses table
    @Test
    void plannedCourseShowsTheCoursesDetails() {
        PlanTerm fall = saveTerm(saveMainPlan(saveStudent("priya@example.com")), 2027, Season.FALL);
        Course course = courseRepository.saveAndFlush(new Course("CMPT", "276", "Introduction to Software Engineering",
                new BigDecimal("3.00"), null, null, null));
        PlannedCourse planned = plannedCourseRepository.saveAndFlush(new PlannedCourse(fall, course));
        entityManager.clear();

        PlannedCourse found = plannedCourseRepository.findById(planned.getId()).orElseThrow();
        assertThat(found.getCode()).isEqualTo("CMPT 276");
        assertThat(found.getTitle()).isEqualTo("Introduction to Software Engineering");
        assertThat(found.getUnits()).isEqualByComparingTo("3");
    }

    @Test
    void studentWithoutAPlanHasNoMainPlan() {
        StudentProfile priya = saveStudent("priya@example.com");
        StudentProfile marcus = saveStudent("marcus@example.com");
        saveMainPlan(priya);

        assertThat(planRepository.findByStudentAndMainTrue(marcus)).isEmpty();
    }

    // A plan that isn't the main one (from #43) isn't returned as the main plan
    @Test
    void onlyTheMainPlanIsFoundAsMain() {
        StudentProfile student = saveStudent("priya@example.com");
        planRepository.saveAndFlush(new Plan(student, "Co-op plan", false));

        assertThat(planRepository.findByStudentAndMainTrue(student)).isEmpty();
    }

    // The safety net under the service's lock: a student can't end up with two plans called "My plan"
    @Test
    void rejectsTwoPlansWithTheSameNameForOneStudent() {
        StudentProfile student = saveStudent("priya@example.com");
        saveMainPlan(student);

        assertThatThrownBy(() -> planRepository.saveAndFlush(new Plan(student, "My plan", true)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentStudentsCanUseTheSamePlanName() {
        saveMainPlan(saveStudent("priya@example.com"));

        saveMainPlan(saveStudent("marcus@example.com"));

        assertThat(planRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsTheSameTermTwiceInOnePlan() {
        Plan plan = saveMainPlan(saveStudent("priya@example.com"));
        saveTerm(plan, 2027, Season.FALL);

        assertThatThrownBy(() -> saveTerm(plan, 2027, Season.FALL))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Two plans, such as a normal and a lighter one, both point at the one 2027 Fall row in terms
    @Test
    void differentPlansShareTheSameTerm() {
        StudentProfile student = saveStudent("priya@example.com");
        Plan main = saveMainPlan(student);
        Plan lighter = planRepository.saveAndFlush(new Plan(student, "Lighter load", false));
        saveTerm(main, 2027, Season.FALL);
        saveTerm(lighter, 2027, Season.FALL);

        assertThat(planTermRepository.existsByPlanAndTerm(main, term(2027, Season.FALL))).isTrue();
        assertThat(planTermRepository.existsByPlanAndTerm(lighter, term(2027, Season.FALL))).isTrue();
        assertThat(planTermRepository.existsByPlanAndTerm(main, term(2028, Season.FALL))).isFalse();
        assertThat(planTermRepository.findAll())
            .extracting(planTerm -> planTerm.getTerm().getId())
            .containsOnly(term(2027, Season.FALL).getId());
    }

    @Test
    void rejectsTheSameCourseTwiceInOneTerm() {
        PlanTerm fall = saveTerm(saveMainPlan(saveStudent("priya@example.com")), 2027, Season.FALL);
        saveCourse(fall, "CMPT", "225");

        assertThatThrownBy(() -> saveCourse(fall, "CMPT", "225"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // The check the service uses to stop a course being planned in two different terms
    @Test
    void findsACourseInAnyTermOfThePlan() {
        Plan plan = saveMainPlan(saveStudent("priya@example.com"));
        saveTerm(plan, 2027, Season.SUMMER);
        PlanTerm fall = saveTerm(plan, 2027, Season.FALL);
        saveCourse(fall, "CMPT", "225");

        assertThat(plannedCourseRepository.findByTermPlanAndCourse(plan, course("CMPT", "225")))
            .map(course -> course.getTerm().getName())
            .contains("2027 Fall");
        assertThat(plannedCourseRepository.findByTermPlanAndCourse(plan, course("CMPT", "276"))).isEmpty();
    }

    @Test
    void anotherStudentCantFindMyTermsOrCourses() {
        StudentProfile priya = saveStudent("priya@example.com");
        StudentProfile marcus = saveStudent("marcus@example.com");
        PlanTerm fall = saveTerm(saveMainPlan(priya), 2027, Season.FALL);
        PlannedCourse course = saveCourse(fall, "CMPT", "225");

        assertThat(planTermRepository.findByIdAndPlanStudent(fall.getId(), priya)).isPresent();
        assertThat(planTermRepository.findByIdAndPlanStudent(fall.getId(), marcus)).isEmpty();
        assertThat(plannedCourseRepository.findByIdAndTermPlanStudent(course.getId(), priya)).isPresent();
        assertThat(plannedCourseRepository.findByIdAndTermPlanStudent(course.getId(), marcus)).isEmpty();
    }

    @Test
    void deletingATermsCoursesLeavesOtherTermsAlone() {
        Plan plan = saveMainPlan(saveStudent("priya@example.com"));
        PlanTerm summer = saveTerm(plan, 2027, Season.SUMMER);
        PlanTerm fall = saveTerm(plan, 2027, Season.FALL);
        saveCourse(summer, "CMPT", "225");
        saveCourse(fall, "CMPT", "276");
        saveCourse(fall, "CMPT", "295");

        assertThat(plannedCourseRepository.deleteByTerm(fall)).isEqualTo(2);
        entityManager.flush();
        entityManager.clear();

        assertThat(plannedCourseRepository.findByTermPlan(plan))
            .extracting(PlannedCourse::getCode)
            .containsExactly("CMPT 225");
    }

    @Test
    void termsSortByWhenTheyHappen() {
        Plan plan = saveMainPlan(saveStudent("priya@example.com"));
        saveTerm(plan, 2028, Season.SPRING);
        saveTerm(plan, 2027, Season.FALL);
        saveTerm(plan, 2027, Season.SUMMER);

        List<PlanTerm> terms = planTermRepository.findByPlan(plan).stream()
            .sorted(PlanTerm.OLDEST_FIRST)
            .toList();

        assertThat(terms).extracting(PlanTerm::getName)
            .containsExactly("2027 Summer", "2027 Fall", "2028 Spring");
    }
}
