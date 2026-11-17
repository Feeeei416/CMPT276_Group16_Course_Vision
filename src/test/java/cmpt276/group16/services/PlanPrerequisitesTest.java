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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.NodeType;
import cmpt276.group16.models.PlanRepository;
import cmpt276.group16.models.PlanTermRepository;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.PlannedCourseRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.RequisiteRuleRepository;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.RuleKind;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// @DataJpaTest only loads repositories, so both services are built by hand.
// PlanService adds the terms and courses the same way the My plan page does
@DataJpaTest
class PlanPrerequisitesTest {

    // 9 November 2026, so the first plannable term is 2027 Summer
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
    private RequisiteRuleRepository requisiteRuleRepository;

    @Autowired
    private TranscriptCourseRepository transcriptCourseRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PlanService planService;
    private PlanPrerequisites planPrerequisites;

    private StudentProfile priya;

    // CMPT 276's one prerequisite rule, so a test can change it
    private RequisiteRule needs225;

    @BeforeEach
    void setUp() {
        planService = new PlanService(planRepository, planTermRepository, plannedCourseRepository, termRepository,
                courseRepository, studentProfileRepository, new TermCreator(termRepository), NOV_9_2026);
        planPrerequisites = new PlanPrerequisites(transcriptCourseRepository, requisiteRuleRepository);
        priya = saveStudent("priya@example.com");

        saveCourse("CMPT", "225");
        Course cmpt276 = saveCourse("CMPT", "276");
        // CMPT 276 needs CMPT 225 with C- or better
        RequisiteRule rule = new RequisiteRule(cmpt276, null, RuleKind.PREREQUISITE, NodeType.COURSE);
        rule.setRequiredSubjectAndNumber("CMPT", "225");
        rule.setMinGrade("C-");
        needs225 = requisiteRuleRepository.saveAndFlush(rule);
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private Course saveCourse(String subject, String number) {
        return courseRepository.saveAndFlush(new Course(subject, number, "Title of " + subject + " " + number,
                new BigDecimal("3.00"), null, null, null));
    }

    private void took(StudentProfile student, String subject, String number, String grade) {
        transcriptCourseRepository.saveAndFlush(new TranscriptCourse(student, "2025 Fall", subject, number,
                "Title", new BigDecimal("3.00"), grade));
    }

    private Long termId(String name) {
        return planService.termPlans(priya).stream()
                .filter(termPlan -> termPlan.term().getName().equals(name))
                .findFirst().orElseThrow().term().getId();
    }

    private void plan(String termName, String code) {
        assertThat(planService.addCourse(priya, termId(termName), code).isSaved()).isTrue();
    }

    // The flags as course code -> missing sentences, read back from the database
    private Map<String, List<String>> flags() {
        entityManager.flush();
        entityManager.clear();
        List<PlanService.TermPlan> termPlans = planService.termPlans(priya);
        Map<Long, List<String>> missing = planPrerequisites.missingFor(priya, termPlans);
        Map<String, List<String>> byCode = new LinkedHashMap<>();
        for (PlanService.TermPlan termPlan : termPlans) {
            for (PlannedCourse course : termPlan.courses()) {
                if (missing.containsKey(course.getId())) {
                    byCode.put(course.getCode(), missing.get(course.getId()));
                }
            }
        }
        return byCode;
    }

    @Test
    void flagsACoursePlannedBeforeItsPrerequisite() {
        planService.addTerm(priya, 2027, "FALL");
        plan("2027 Fall", "CMPT 276");

        assertThat(flags()).containsExactly(entry("CMPT 276", List.of("Needs CMPT 225 with C- or better.")));
    }

    @Test
    void passedPrerequisiteInTheRecordMeetsIt() {
        took(priya, "CMPT", "225", "B");
        planService.addTerm(priya, 2027, "FALL");
        plan("2027 Fall", "CMPT 276");

        assertThat(flags()).isEmpty();
    }

    // A grade below the minimum is named, so the student knows why it doesn't count
    @Test
    void lowGradeInTheRecordIsNamed() {
        took(priya, "CMPT", "225", "D");
        planService.addTerm(priya, 2027, "FALL");
        plan("2027 Fall", "CMPT 276");

        assertThat(flags()).containsExactly(entry("CMPT 276", List.of("Needs CMPT 225 with C- or better, you have D.")));
    }

    @Test
    void prerequisitePlannedForAnEarlierTermMeetsIt() {
        planService.addTerm(priya, 2027, "SUMMER");
        planService.addTerm(priya, 2027, "FALL");
        plan("2027 Summer", "CMPT 225");
        plan("2027 Fall", "CMPT 276");

        assertThat(flags()).isEmpty();
    }

    // The rule doesn't say CMPT 225 may be taken concurrently, so both in the same term isn't enough
    @Test
    void prerequisiteInTheSameTermDoesntMeetIt() {
        planService.addTerm(priya, 2027, "FALL");
        plan("2027 Fall", "CMPT 225");
        plan("2027 Fall", "CMPT 276");

        assertThat(flags()).containsOnlyKeys("CMPT 276");
    }

    @Test
    void prerequisiteThatMayBeTakenConcurrentlyMeetsItInTheSameTerm() {
        needs225.setConcurrentOk(true);
        requisiteRuleRepository.saveAndFlush(needs225);
        planService.addTerm(priya, 2027, "FALL");
        plan("2027 Fall", "CMPT 225");
        plan("2027 Fall", "CMPT 276");

        assertThat(flags()).isEmpty();
    }

    // May be taken concurrently, but not later
    @Test
    void prerequisiteThatMayBeTakenConcurrentlyStillCantBeLater() {
        needs225.setConcurrentOk(true);
        requisiteRuleRepository.saveAndFlush(needs225);
        planService.addTerm(priya, 2027, "FALL");
        planService.addTerm(priya, 2028, "SPRING");
        plan("2027 Fall", "CMPT 276");
        plan("2028 Spring", "CMPT 225");

        assertThat(flags()).containsOnlyKeys("CMPT 276");
    }

    @Test
    void prerequisitePlannedForALaterTermDoesntMeetIt() {
        planService.addTerm(priya, 2027, "FALL");
        planService.addTerm(priya, 2028, "SPRING");
        plan("2027 Fall", "CMPT 276");
        plan("2028 Spring", "CMPT 225");

        assertThat(flags()).containsOnlyKeys("CMPT 276");
    }

    // #44: moving the missing course to an earlier term clears the flag
    @Test
    void movingThePrerequisiteEarlierClearsTheFlag() {
        planService.addTerm(priya, 2027, "SUMMER");
        planService.addTerm(priya, 2027, "FALL");
        planService.addTerm(priya, 2028, "SPRING");
        plan("2027 Fall", "CMPT 276");
        plan("2028 Spring", "CMPT 225");
        assertThat(flags()).containsOnlyKeys("CMPT 276");

        Long planned225 = planService.termPlans(priya).stream()
                .flatMap(termPlan -> termPlan.courses().stream())
                .filter(course -> course.getCode().equals("CMPT 225"))
                .findFirst().orElseThrow().getId();
        planService.removeCourse(priya, planned225);
        plan("2027 Summer", "CMPT 225");

        assertThat(flags()).isEmpty();
    }

    // Only Priya's own record counts
    @Test
    void anotherStudentsRecordDoesntCount() {
        took(saveStudent("marcus@example.com"), "CMPT", "225", "A");
        planService.addTerm(priya, 2027, "FALL");
        plan("2027 Fall", "CMPT 276");

        assertThat(flags()).containsOnlyKeys("CMPT 276");
    }
}
