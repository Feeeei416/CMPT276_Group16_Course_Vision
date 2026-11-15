package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.Plan;
import cmpt276.group16.models.PlanRepository;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlanTermRepository;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.PlannedCourseRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermKind;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.PlanListService.PlanSummary;

// @DataJpaTest only loads repositories, so the service is built by hand
@DataJpaTest
class PlanListServiceTest {

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

    private PlanListService planListService;

    private StudentProfile priya;
    private StudentProfile marcus;

    @BeforeEach
    void setUp() {
        planListService = new PlanListService(planRepository, planTermRepository, plannedCourseRepository,
                studentProfileRepository);
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private Plan savePlan(StudentProfile student, String name, boolean main) {
        return planRepository.saveAndFlush(new Plan(student, name, main));
    }

    private PlanTerm saveTerm(Plan plan, int year, Season season, TermKind kind) {
        Term term = termRepository.findByTermYearAndSeason(year, season)
                .orElseGet(() -> termRepository.saveAndFlush(new Term(year, season)));
        return planTermRepository.saveAndFlush(new PlanTerm(plan, term, kind));
    }

    private void saveCourse(PlanTerm term, String subject, String number) {
        Course course = courseRepository.findBySubjectAndNumber(subject, number)
                .orElseGet(() -> courseRepository.saveAndFlush(new Course(subject, number,
                        "Title of " + subject + " " + number, new BigDecimal("3.00"), null, null, null)));
        plannedCourseRepository.saveAndFlush(new PlannedCourse(term, course));
    }

    // Reads the student's plans back from the database, main plan first
    private List<PlanSummary> plansOf(StudentProfile student) {
        entityManager.flush();
        entityManager.clear();
        return planListService.plans(student);
    }

    private Plan planNamed(StudentProfile student, String name) {
        return plansOf(student).stream().map(PlanSummary::plan)
                .filter(plan -> plan.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void listsTheMainPlanFirstThenTheRestInTheOrderTheyWereMade() {
        savePlan(priya, "Lighter load", false);
        savePlan(priya, "My plan", true);
        savePlan(priya, "Co-op plan", false);
        savePlan(marcus, "Marcus's plan", true);

        assertThat(plansOf(priya)).extracting(summary -> summary.plan().getName())
            .containsExactly("My plan", "Lighter load", "Co-op plan");
    }

    @Test
    void eachPlanSaysHowManyTermsAndCoursesItHas() {
        Plan plan = savePlan(priya, "My plan", true);
        PlanTerm fall = saveTerm(plan, 2027, Season.FALL, TermKind.STUDY);
        saveTerm(plan, 2028, Season.SPRING, TermKind.COOP);
        saveCourse(fall, "CMPT", "225");
        savePlan(priya, "Empty plan", false);

        assertThat(plansOf(priya)).extracting(PlanSummary::sizeText)
            .containsExactly("2 terms, 1 course", "0 terms, 0 courses");
    }

    @Test
    void firstPlanBecomesTheMainPlan() {
        PlanChange change = planListService.create(priya, "  Co-op plan ", null);

        assertThat(change.message()).isEqualTo("Made Co-op plan.");
        assertThat(plansOf(priya)).extracting(summary -> summary.plan().getName(), summary -> summary.plan().isMain())
            .containsExactly(tuple("Co-op plan", true));
    }

    @Test
    void laterPlansStartEmptyAndArentMain() {
        savePlan(priya, "My plan", true);

        planListService.create(priya, "Lighter load", null);

        Plan lighter = planNamed(priya, "Lighter load");
        assertThat(lighter.isMain()).isFalse();
        assertThat(planTermRepository.findByPlan(lighter)).isEmpty();
    }

    @Test
    void copyHasTheSameTermsKindsAndCourses() {
        Plan main = savePlan(priya, "My plan", true);
        PlanTerm fall = saveTerm(main, 2027, Season.FALL, TermKind.STUDY);
        saveTerm(main, 2028, Season.SPRING, TermKind.COOP);
        saveCourse(fall, "CMPT", "225");
        saveCourse(fall, "CMPT", "276");

        PlanChange change = planListService.create(priya, "Co-op plan", main.getId());

        assertThat(change.message()).isEqualTo("Made Co-op plan as a copy of My plan.");
        Plan copy = planNamed(priya, "Co-op plan");
        assertThat(copy.isMain()).isFalse();
        assertThat(planTermRepository.findByPlan(copy).stream().sorted(PlanTerm.OLDEST_FIRST))
            .extracting(PlanTerm::getName, PlanTerm::getKind)
            .containsExactly(tuple("2027 Fall", TermKind.STUDY),
                    tuple("2028 Spring", TermKind.COOP));
        assertThat(plannedCourseRepository.findByTermPlan(copy)).extracting(PlannedCourse::getCode)
            .containsExactlyInAnyOrder("CMPT 225", "CMPT 276");
    }

    // Changing the copy afterwards mustn't change the plan it was copied from
    @Test
    void copyIsSeparateFromTheOriginal() {
        Plan main = savePlan(priya, "My plan", true);
        saveCourse(saveTerm(main, 2027, Season.FALL, TermKind.STUDY), "CMPT", "225");
        planListService.create(priya, "Co-op plan", main.getId());

        planListService.delete(priya, planNamed(priya, "Co-op plan").getId());

        assertThat(plansOf(priya)).extracting(PlanSummary::sizeText).containsExactly("1 term, 1 course");
    }

    @Test
    void cantCopyAnotherStudentsPlan() {
        Plan marcusPlan = savePlan(marcus, "Marcus's plan", true);
        savePlan(priya, "My plan", true);

        PlanChange change = planListService.create(priya, "Stolen", marcusPlan.getId());

        assertThat(change.problems()).containsExactly("The plan to copy isn't one of your plans.");
        assertThat(plansOf(priya)).hasSize(1);
    }

    @Test
    void cantUseANameTheStudentAlreadyHasInAnyCase() {
        savePlan(priya, "Co-op plan", true);

        PlanChange change = planListService.create(priya, "CO-OP PLAN", null);

        assertThat(change.problems()).containsExactly("You already have a plan called CO-OP PLAN.");
    }

    @Test
    void anotherStudentCanUseTheSameName() {
        savePlan(marcus, "Co-op plan", true);

        assertThat(planListService.create(priya, "Co-op plan", null).isSaved()).isTrue();
    }

    @Test
    void rejectsABlankName() {
        assertThat(planListService.create(priya, "   ", null).problems()).containsExactly("Give the plan a name.");
        assertThat(plansOf(priya)).isEmpty();
    }

    @Test
    void renamesAPlan() {
        Plan plan = savePlan(priya, "My plan", true);

        PlanChange change = planListService.rename(priya, plan.getId(), " Four-year plan ");

        assertThat(change.message()).isEqualTo("Renamed My plan to Four-year plan.");
        assertThat(planNamed(priya, "Four-year plan").isMain()).isTrue();
    }

    @Test
    void renamingCanFixOnlyTheCapitals() {
        Plan plan = savePlan(priya, "co-op plan", true);

        assertThat(planListService.rename(priya, plan.getId(), "Co-op plan").isSaved()).isTrue();
        assertThat(planNamed(priya, "Co-op plan")).isNotNull();
    }

    @Test
    void cantRenameToAnotherPlansName() {
        savePlan(priya, "My plan", true);
        Plan coop = savePlan(priya, "Co-op plan", false);

        assertThat(planListService.rename(priya, coop.getId(), "my plan").problems())
            .containsExactly("You already have a plan called my plan.");
    }

    @Test
    void renamingToTheSameNameSaysSo() {
        Plan plan = savePlan(priya, "My plan", true);

        assertThat(planListService.rename(priya, plan.getId(), "My plan").problems())
            .containsExactly("My plan already has that name.");
    }

    @Test
    void deletesAPlanWithItsTermsAndCourses() {
        savePlan(priya, "My plan", true);
        Plan coop = savePlan(priya, "Co-op plan", false);
        saveCourse(saveTerm(coop, 2027, Season.FALL, TermKind.STUDY), "CMPT", "225");
        saveTerm(coop, 2028, Season.SPRING, TermKind.COOP);

        PlanChange change = planListService.delete(priya, coop.getId());

        assertThat(change.message()).isEqualTo("Deleted Co-op plan.");
        assertThat(plansOf(priya)).extracting(summary -> summary.plan().getName()).containsExactly("My plan");
        assertThat(planTermRepository.count()).isZero();
        assertThat(plannedCourseRepository.count()).isZero();
    }

    // There's always a main plan for the Graduation Report to read
    @Test
    void cantDeleteTheMainPlan() {
        Plan main = savePlan(priya, "My plan", true);

        assertThat(planListService.delete(priya, main.getId()).problems())
            .containsExactly("My plan is your main plan. Make another plan your main plan before deleting it.");
        assertThat(plansOf(priya)).hasSize(1);
    }

    @Test
    void makingAPlanMainUnmarksTheOldOne() {
        savePlan(priya, "My plan", true);
        Plan coop = savePlan(priya, "Co-op plan", false);

        PlanChange change = planListService.makeMain(priya, coop.getId());

        assertThat(change.message()).isEqualTo("Co-op plan is now your main plan.");
        assertThat(plansOf(priya)).extracting(summary -> summary.plan().getName(), summary -> summary.plan().isMain())
            .containsExactly(tuple("Co-op plan", true),
                    tuple("My plan", false));
    }

    @Test
    void makingTheMainPlanMainAgainSaysSo() {
        Plan main = savePlan(priya, "My plan", true);

        assertThat(planListService.makeMain(priya, main.getId()).problems())
            .containsExactly("My plan is already your main plan.");
    }

    // Changing the id in the request finds nothing, and Marcus's plan stays as it was
    @Test
    void cantChangeAnotherStudentsPlan() {
        savePlan(marcus, "Marcus's plan", true);
        Plan marcusCoop = savePlan(marcus, "Marcus's co-op", false);
        savePlan(priya, "My plan", true);
        Long id = marcusCoop.getId();

        assertThat(planListService.rename(priya, id, "Mine now").problems()).containsExactly("That plan isn't one of yours.");
        assertThat(planListService.makeMain(priya, id).problems()).containsExactly("That plan isn't one of yours.");
        assertThat(planListService.delete(priya, id).problems()).containsExactly("That plan isn't one of yours.");
        assertThat(planListService.find(priya, id)).isEmpty();
        assertThat(plansOf(marcus)).extracting(summary -> summary.plan().getName())
            .containsExactly("Marcus's plan", "Marcus's co-op");
    }
}
