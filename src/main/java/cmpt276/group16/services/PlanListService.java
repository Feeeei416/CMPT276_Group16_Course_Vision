package cmpt276.group16.services;

import java.util.Comparator;
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
import cmpt276.group16.models.StudentProfileRepository;

// A student's plans as a whole, for the All my plans page: making, copying, renaming and deleting them, and choosing
// which one is the main plan. PlanService changes what's inside a plan.
// Each change locks the student first, like PlanService does, so a double click can't make two plans with the
// same name or leave two main plans
@Service
public class PlanListService {

    private final PlanRepository planRepository;
    private final PlanTermRepository planTermRepository;
    private final PlannedCourseRepository plannedCourseRepository;
    private final StudentProfileRepository studentProfileRepository;

    public PlanListService(PlanRepository planRepository, PlanTermRepository planTermRepository,
            PlannedCourseRepository plannedCourseRepository, StudentProfileRepository studentProfileRepository) {
        this.planRepository = planRepository;
        this.planTermRepository = planTermRepository;
        this.plannedCourseRepository = plannedCourseRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    // One row on the All my plans page
    public record PlanSummary(Plan plan, int termCount, int courseCount) {

        // For example "3 terms, 9 courses", or "1 term, 1 course"
        public String sizeText() {
            return count(termCount, "term") + ", " + count(courseCount, "course");
        }

        private static String count(int n, String word) {
            return n + " " + word + (n == 1 ? "" : "s");
        }
    }

    // The main plan first, then the rest in the order they were made
    @Transactional(readOnly = true)
    public List<PlanSummary> plans(StudentProfile student) {
        return planRepository.findByStudentOrderByIdAsc(student).stream()
                .sorted(Comparator.comparing((Plan plan) -> !plan.isMain()))
                .map(this::summarize)
                .toList();
    }

    // Empty when the plan is another student's
    @Transactional(readOnly = true)
    public Optional<Plan> find(StudentProfile student, Long planId) {
        return planRepository.findByIdAndStudent(planId, student);
    }

    // copyFromId is null for an empty plan. A student's first plan becomes their main plan, since there has to be one
    @Transactional
    public PlanChange create(StudentProfile student, String nameText, Long copyFromId) {
        lock(student);
        String name = nameText.trim();
        List<String> problems = problemsWithNewName(student, name);
        if (!problems.isEmpty()) {
            return PlanChange.rejected(problems);
        }
        if (copyFromId == null) {
            boolean first = planRepository.findByStudentOrderByIdAsc(student).isEmpty();
            planRepository.save(new Plan(student, name, first));
            return PlanChange.saved("Made " + name + ".");
        }
        Optional<Plan> source = planRepository.findByIdAndStudent(copyFromId, student);
        if (source.isEmpty()) {
            return PlanChange.rejected("The plan to copy isn't one of your plans.");
        }
        Plan copy = planRepository.save(new Plan(student, name, false));
        copyTermsAndCourses(source.get(), copy);
        return PlanChange.saved("Made " + name + " as a copy of " + source.get().getName() + ".");
    }

    @Transactional
    public PlanChange rename(StudentProfile student, Long planId, String nameText) {
        lock(student);
        Optional<Plan> plan = planRepository.findByIdAndStudent(planId, student);
        if (plan.isEmpty()) {
            return PlanChange.rejected("That plan isn't one of yours.");
        }
        String oldName = plan.get().getName();
        String name = nameText.trim();
        if (name.equals(oldName)) {
            return PlanChange.rejected(name + " already has that name.");
        }
        // Changing only the capitals, like "co-op plan" to "Co-op plan", is allowed
        List<String> problems = name.equalsIgnoreCase(oldName)
                ? PlanRules.problemsWithPlanName(name)
                : problemsWithNewName(student, name);
        if (!problems.isEmpty()) {
            return PlanChange.rejected(problems);
        }
        plan.get().setName(name);
        return PlanChange.saved("Renamed " + oldName + " to " + name + ".");
    }

    // The main plan can't be deleted, so the student always has one for the Graduation Report to read.
    // Courses go first, then terms, then the plan, since each row points at the one above it
    @Transactional
    public PlanChange delete(StudentProfile student, Long planId) {
        lock(student);
        Optional<Plan> plan = planRepository.findByIdAndStudent(planId, student);
        if (plan.isEmpty()) {
            return PlanChange.rejected("That plan isn't one of yours.");
        }
        String name = plan.get().getName();
        if (plan.get().isMain()) {
            return PlanChange.rejected(name + " is your main plan. Make another plan your main plan before deleting it.");
        }
        plannedCourseRepository.deleteByTermPlan(plan.get());
        planTermRepository.deleteByPlan(plan.get());
        planRepository.delete(plan.get());
        return PlanChange.saved("Deleted " + name + ".");
    }

    @Transactional
    public PlanChange makeMain(StudentProfile student, Long planId) {
        lock(student);
        Optional<Plan> plan = planRepository.findByIdAndStudent(planId, student);
        if (plan.isEmpty()) {
            return PlanChange.rejected("That plan isn't one of yours.");
        }
        String name = plan.get().getName();
        if (plan.get().isMain()) {
            return PlanChange.rejected(name + " is already your main plan.");
        }
        planRepository.findByStudentAndMainTrue(student).ifPresent(oldMain -> oldMain.setMain(false));
        plan.get().setMain(true);
        return PlanChange.saved(name + " is now your main plan.");
    }

    private List<String> problemsWithNewName(StudentProfile student, String name) {
        List<String> problems = PlanRules.problemsWithPlanName(name);
        if (problems.isEmpty() && planRepository.existsByStudentAndNameIgnoreCase(student, name)) {
            return List.of("You already have a plan called " + name + ".");
        }
        return problems;
    }

    // Each term keeps its kind, so a co-op term is still a co-op term in the copy
    private void copyTermsAndCourses(Plan source, Plan copy) {
        Map<Long, List<PlannedCourse>> coursesByTermId = plannedCourseRepository.findByTermPlan(source).stream()
                .collect(Collectors.groupingBy(course -> course.getTerm().getId()));
        for (PlanTerm term : planTermRepository.findByPlan(source)) {
            PlanTerm copiedTerm = planTermRepository.save(new PlanTerm(copy, term.getTerm(), term.getKind()));
            for (PlannedCourse course : coursesByTermId.getOrDefault(term.getId(), List.of())) {
                plannedCourseRepository.save(new PlannedCourse(copiedTerm, course.getCourse()));
            }
        }
    }

    private PlanSummary summarize(Plan plan) {
        return new PlanSummary(plan, planTermRepository.findByPlan(plan).size(),
                plannedCourseRepository.findByTermPlan(plan).size());
    }

    // Held until the change's transaction ends; another change for the same student waits here
    private void lock(StudentProfile student) {
        studentProfileRepository.findLockedByUserId(student.getUserId());
    }
}
