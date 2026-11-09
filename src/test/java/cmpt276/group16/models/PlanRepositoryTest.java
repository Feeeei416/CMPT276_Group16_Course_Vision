package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    private TestEntityManager entityManager;

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private Plan saveMainPlan(StudentProfile student) {
        return planRepository.saveAndFlush(new Plan(student, "My plan", true));
    }

    private PlanTerm saveTerm(Plan plan, int year, String season) {
        return planTermRepository.saveAndFlush(new PlanTerm(plan, year, season));
    }

    private PlannedCourse saveCourse(PlanTerm term, String subject, String number) {
        return plannedCourseRepository.saveAndFlush(new PlannedCourse(term, subject, number));
    }

    @Test
    void planWithTermsAndCoursesIsSavedAndFound() {
        StudentProfile student = saveStudent("priya@example.com");
        Plan plan = saveMainPlan(student);
        PlanTerm fall = saveTerm(plan, 2027, "Fall");
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

    @Test
    void rejectsTheSameTermTwiceInOnePlan() {
        Plan plan = saveMainPlan(saveStudent("priya@example.com"));
        saveTerm(plan, 2027, "Fall");

        assertThatThrownBy(() -> saveTerm(plan, 2027, "Fall"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Two plans, such as a normal and a lighter one, can both have 2027 Fall
    @Test
    void differentPlansCanHaveTheSameTerm() {
        StudentProfile student = saveStudent("priya@example.com");
        Plan main = saveMainPlan(student);
        Plan lighter = planRepository.saveAndFlush(new Plan(student, "Lighter load", false));
        saveTerm(main, 2027, "Fall");
        saveTerm(lighter, 2027, "Fall");

        assertThat(planTermRepository.existsByPlanAndYearAndSeason(main, 2027, "Fall")).isTrue();
        assertThat(planTermRepository.existsByPlanAndYearAndSeason(lighter, 2027, "Fall")).isTrue();
        assertThat(planTermRepository.existsByPlanAndYearAndSeason(main, 2028, "Fall")).isFalse();
    }

    @Test
    void rejectsTheSameCourseTwiceInOneTerm() {
        PlanTerm fall = saveTerm(saveMainPlan(saveStudent("priya@example.com")), 2027, "Fall");
        saveCourse(fall, "CMPT", "225");

        assertThatThrownBy(() -> saveCourse(fall, "CMPT", "225"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // The check the service uses to stop a course being planned in two different terms
    @Test
    void findsACourseInAnyTermOfThePlan() {
        Plan plan = saveMainPlan(saveStudent("priya@example.com"));
        saveTerm(plan, 2027, "Summer");
        PlanTerm fall = saveTerm(plan, 2027, "Fall");
        saveCourse(fall, "CMPT", "225");

        assertThat(plannedCourseRepository.findByTermPlanAndSubjectAndNumber(plan, "CMPT", "225"))
            .map(course -> course.getTerm().getName())
            .contains("2027 Fall");
        assertThat(plannedCourseRepository.findByTermPlanAndSubjectAndNumber(plan, "CMPT", "276")).isEmpty();
    }

    @Test
    void anotherStudentCantFindMyTermsOrCourses() {
        StudentProfile priya = saveStudent("priya@example.com");
        StudentProfile marcus = saveStudent("marcus@example.com");
        PlanTerm fall = saveTerm(saveMainPlan(priya), 2027, "Fall");
        PlannedCourse course = saveCourse(fall, "CMPT", "225");

        assertThat(planTermRepository.findByIdAndPlanStudent(fall.getId(), priya)).isPresent();
        assertThat(planTermRepository.findByIdAndPlanStudent(fall.getId(), marcus)).isEmpty();
        assertThat(plannedCourseRepository.findByIdAndTermPlanStudent(course.getId(), priya)).isPresent();
        assertThat(plannedCourseRepository.findByIdAndTermPlanStudent(course.getId(), marcus)).isEmpty();
    }

    @Test
    void deletingATermsCoursesLeavesOtherTermsAlone() {
        Plan plan = saveMainPlan(saveStudent("priya@example.com"));
        PlanTerm summer = saveTerm(plan, 2027, "Summer");
        PlanTerm fall = saveTerm(plan, 2027, "Fall");
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
        saveTerm(plan, 2028, "Spring");
        saveTerm(plan, 2027, "Fall");
        saveTerm(plan, 2027, "Summer");

        List<PlanTerm> terms = planTermRepository.findByPlan(plan).stream()
            .sorted(PlanTerm.OLDEST_FIRST)
            .toList();

        assertThat(terms).extracting(PlanTerm::getName)
            .containsExactly("2027 Summer", "2027 Fall", "2028 Spring");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Winter", "fall", ""})
    void rejectsUnknownSeasons(String season) {
        Plan plan = saveMainPlan(saveStudent("priya@example.com"));

        assertThatThrownBy(() -> new PlanTerm(plan, 2027, season))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
