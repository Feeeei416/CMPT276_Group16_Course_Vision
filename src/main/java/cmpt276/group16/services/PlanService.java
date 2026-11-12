package cmpt276.group16.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.Plan;
import cmpt276.group16.models.PlanRepository;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlanTermRepository;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.PlannedCourseRepository;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.services.PlanRules.CourseCode;

// Changes to a student's plan. Each change returns every problem found; an empty list means it was saved.
// Each change first locks the student, the same way TranscriptService does: changes check what's already in the
// plan and then save, so two requests at once (a double click) would both pass the check. With the lock, the
// second request waits and then sees what the first one saved
@Service
public class PlanService {

    public static final String MAIN_PLAN_NAME = "My plan";

    private final PlanRepository planRepository;
    private final PlanTermRepository planTermRepository;
    private final PlannedCourseRepository plannedCourseRepository;
    private final TermRepository termRepository;
    private final CourseRepository courseRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final Clock clock;

    public PlanService(PlanRepository planRepository, PlanTermRepository planTermRepository,
            PlannedCourseRepository plannedCourseRepository, TermRepository termRepository,
            CourseRepository courseRepository, StudentProfileRepository studentProfileRepository, Clock clock) {
        this.planRepository = planRepository;
        this.planTermRepository = planTermRepository;
        this.plannedCourseRepository = plannedCourseRepository;
        this.termRepository = termRepository;
        this.courseRepository = courseRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.clock = clock;
    }

    // One term as the My plan page shows it: the term, its courses in the order they were added, and their units
    public record TermPlan(PlanTerm term, List<PlannedCourse> courses) {

        // For example "9 units", or "1 unit"
        public String unitsText() {
            BigDecimal total = courses.stream().map(PlannedCourse::getUnits).reduce(BigDecimal.ZERO, BigDecimal::add);
            String units = total.stripTrailingZeros().toPlainString();
            return units + (total.compareTo(BigDecimal.ONE) == 0 ? " unit" : " units");
        }
    }

    // Every term in the student's main plan, oldest first.
    // Empty until the student adds their first term, so opening the page never writes to the database
    @Transactional(readOnly = true)
    public List<TermPlan> termPlans(StudentProfile student) {
        Optional<Plan> plan = planRepository.findByStudentAndMainTrue(student);
        if (plan.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PlannedCourse>> coursesByTermId = plannedCourseRepository.findByTermPlan(plan.get()).stream()
                .sorted(Comparator.comparing(PlannedCourse::getId))
                .collect(Collectors.groupingBy(course -> course.getTerm().getId()));
        return planTermRepository.findByPlan(plan.get()).stream()
                .sorted(PlanTerm.OLDEST_FIRST)
                .map(term -> new TermPlan(term, coursesByTermId.getOrDefault(term.getId(), List.of())))
                .toList();
    }

    @Transactional
    public List<String> addTerm(StudentProfile student, int year, String seasonText) {
        lock(student);
        Optional<Season> season = PlanRules.parseSeason(seasonText);
        if (season.isEmpty()) {
            return List.of("Choose Spring, Summer or Fall.");
        }
        List<String> problems = PlanRules.problemsWithTerm(year, season.get(), today());
        if (!problems.isEmpty()) {
            return problems;
        }
        Plan plan = mainPlanFor(student);
        Term term = termFor(year, season.get());
        if (planTermRepository.existsByPlanAndTerm(plan, term)) {
            return List.of(PlanTerm.nameOf(year, season.get()) + " is already in your plan.");
        }
        planTermRepository.save(new PlanTerm(plan, term));
        return List.of();
    }

    // Removes the term and every course in it together, so a failed delete leaves both in place
    @Transactional
    public List<String> removeTerm(StudentProfile student, Long termId) {
        lock(student);
        Optional<PlanTerm> term = planTermRepository.findByIdAndPlanStudent(termId, student);
        if (term.isEmpty()) {
            return List.of("That term isn't in your plan.");
        }
        plannedCourseRepository.deleteByTerm(term.get());
        planTermRepository.delete(term.get());
        return List.of();
    }

    @Transactional
    public List<String> addCourse(StudentProfile student, Long termId, String code) {
        lock(student);
        Optional<PlanTerm> term = planTermRepository.findByIdAndPlanStudent(termId, student);
        if (term.isEmpty()) {
            return List.of("That term isn't in your plan.");
        }
        Optional<CourseCode> parsed = PlanRules.parseCourseCode(code);
        if (parsed.isEmpty()) {
            return List.of("Enter a course code like CMPT 225.");
        }
        Optional<Course> course = courseRepository.findBySubjectAndNumber(parsed.get().subject(), parsed.get().number());
        if (course.isEmpty()) {
            return List.of(parsed.get().subject() + " " + parsed.get().number() + " isn't in the course list.");
        }
        // Checks the whole plan, not just this term, since a course only needs to be taken once
        Optional<PlannedCourse> alreadyPlanned = plannedCourseRepository
                .findByTermPlanAndCourse(term.get().getPlan(), course.get());
        if (alreadyPlanned.isPresent()) {
            return List.of(alreadyPlannedMessage(alreadyPlanned.get(), term.get()));
        }
        plannedCourseRepository.save(new PlannedCourse(term.get(), course.get()));
        return List.of();
    }

    // Says which term the course is already in, and how to move it when that's a different term
    private static String alreadyPlannedMessage(PlannedCourse existing, PlanTerm wanted) {
        String code = existing.getCode();
        String existingTerm = existing.getTerm().getName();
        if (existing.getTerm().getId().equals(wanted.getId())) {
            return code + " is already in " + existingTerm + ".";
        }
        return code + " is already planned for " + existingTerm + ". To move it to " + wanted.getName()
                + ", remove it from " + existingTerm + " first.";
    }

    @Transactional
    public List<String> removeCourse(StudentProfile student, Long courseId) {
        lock(student);
        Optional<PlannedCourse> course = plannedCourseRepository.findByIdAndTermPlanStudent(courseId, student);
        if (course.isEmpty()) {
            return List.of("That course isn't in your plan.");
        }
        plannedCourseRepository.delete(course.get());
        return List.of();
    }

    // For the My plan page, so it doesn't need the clock itself
    public String nextSemester() {
        return PlanRules.nextSemester(today());
    }

    public List<Integer> plannableYears() {
        return PlanRules.plannableYears(today());
    }

    // The student's main plan, created the first time they add a term
    private Plan mainPlanFor(StudentProfile student) {
        return planRepository.findByStudentAndMainTrue(student)
                .orElseGet(() -> planRepository.save(new Plan(student, MAIN_PLAN_NAME, true)));
    }

    // Plans reach terms the course import hasn't seen yet, so the shared term is created if it's missing
    private Term termFor(int year, Season season) {
        return termRepository.findByTermYearAndSeason(year, season)
                .orElseGet(() -> termRepository.save(new Term(year, season)));
    }

    // Held until the change's transaction ends; another change for the same student waits here
    private void lock(StudentProfile student) {
        studentProfileRepository.findLockedByUserId(student.getUserId());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
