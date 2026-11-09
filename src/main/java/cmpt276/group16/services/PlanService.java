package cmpt276.group16.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Plan;
import cmpt276.group16.models.PlanRepository;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlanTermRepository;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.PlannedCourseRepository;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.services.PlanRules.CourseCode;

// Changes to a student's plan. Each change returns every problem found; an empty list means it was saved
@Service
public class PlanService {

    public static final String MAIN_PLAN_NAME = "My plan";

    private final PlanRepository planRepository;
    private final PlanTermRepository planTermRepository;
    private final PlannedCourseRepository plannedCourseRepository;
    private final Clock clock;

    public PlanService(PlanRepository planRepository, PlanTermRepository planTermRepository,
            PlannedCourseRepository plannedCourseRepository, Clock clock) {
        this.planRepository = planRepository;
        this.planTermRepository = planTermRepository;
        this.plannedCourseRepository = plannedCourseRepository;
        this.clock = clock;
    }

    // Every term in the student's main plan, oldest first, each with its courses in the order they were added.
    // Empty until the student adds their first term, so opening the page never writes to the database
    @Transactional(readOnly = true)
    public Map<PlanTerm, List<PlannedCourse>> coursesByTerm(StudentProfile student) {
        Optional<Plan> plan = planRepository.findByStudentAndMainTrue(student);
        if (plan.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<PlannedCourse>> coursesByTermId = plannedCourseRepository.findByTermPlan(plan.get()).stream()
                .sorted(Comparator.comparing(PlannedCourse::getId))
                .collect(Collectors.groupingBy(course -> course.getTerm().getId()));
        Map<PlanTerm, List<PlannedCourse>> result = new LinkedHashMap<>();
        planTermRepository.findByPlan(plan.get()).stream()
                .sorted(PlanTerm.OLDEST_FIRST)
                .forEach(term -> result.put(term, coursesByTermId.getOrDefault(term.getId(), List.of())));
        return result;
    }

    @Transactional
    public List<String> addTerm(StudentProfile student, int year, String season) {
        List<String> problems = PlanRules.problemsWithTerm(year, season, today());
        if (!problems.isEmpty()) {
            return problems;
        }
        Plan plan = mainPlanFor(student);
        if (planTermRepository.existsByPlanAndYearAndSeason(plan, year, season)) {
            return List.of(year + " " + season + " is already in your plan.");
        }
        planTermRepository.save(new PlanTerm(plan, year, season));
        return List.of();
    }

    // Removes the term and every course in it together, so a failed delete leaves both in place
    @Transactional
    public List<String> removeTerm(StudentProfile student, Long termId) {
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
        Optional<PlanTerm> term = planTermRepository.findByIdAndPlanStudent(termId, student);
        if (term.isEmpty()) {
            return List.of("That term isn't in your plan.");
        }
        Optional<CourseCode> course = PlanRules.parseCourseCode(code);
        if (course.isEmpty()) {
            return List.of("Enter a course code like CMPT 225.");
        }
        String subject = course.get().subject();
        String number = course.get().number();
        // Checks the whole plan, not just this term, since a course only needs to be taken once
        Optional<PlannedCourse> alreadyPlanned = plannedCourseRepository
                .findByTermPlanAndSubjectAndNumber(term.get().getPlan(), subject, number);
        if (alreadyPlanned.isPresent()) {
            return List.of(alreadyPlannedMessage(alreadyPlanned.get(), term.get()));
        }
        plannedCourseRepository.save(new PlannedCourse(term.get(), subject, number));
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

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
