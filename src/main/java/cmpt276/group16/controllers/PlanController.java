package cmpt276.group16.controllers;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.models.Plan;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.services.PlanChange;
import cmpt276.group16.services.PlanListService;
import cmpt276.group16.services.PlanService;
import jakarta.servlet.http.HttpSession;

// My plan: the courses a student plans to take in the terms after next semester.
// /student/plan shows the main plan, and /student/plans/5 shows any one of the student's plans on the same page.
// Every change redirects back to the page it came from, so refreshing it doesn't send the form again
@Controller
public class PlanController {

    private final CurrentStudent currentStudent;
    private final PlanService planService;
    private final PlanListService planListService;

    public PlanController(CurrentStudent currentStudent, PlanService planService, PlanListService planListService) {
        this.currentStudent = currentStudent;
        this.planService = planService;
        this.planListService = planListService;
    }

    @GetMapping("/student/plan")
    public String showPlan(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return showPlanPage(planService.mainPlan(student.get()).orElse(null), null, model);
    }

    @GetMapping("/student/plans/{planId}")
    public String showOnePlan(@PathVariable Long planId, HttpSession session, Model model,
                              RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        Optional<Plan> plan = planListService.find(student.get(), planId);
        if (plan.isEmpty()) {
            redirectAttributes.addFlashAttribute("errors", List.of("That plan isn't one of yours."));
            return "redirect:/student/plans";
        }
        return showPlanPage(plan.get(), planId, model);
    }

    // plan is null when the student has no plan yet. viewedPlanId is set when the page was opened as
    // /student/plans/5, so its forms send it back and each change returns to that plan
    private String showPlanPage(Plan plan, Long viewedPlanId, Model model) {
        model.addAttribute("plan", plan);
        model.addAttribute("viewedPlanId", viewedPlanId);
        model.addAttribute("termPlans", plan == null ? List.of() : planService.termPlans(plan));
        model.addAttribute("nextSemester", planService.nextSemester());
        model.addAttribute("years", planService.plannableYears());
        model.addAttribute("seasons", Season.values());
        // Already set when the change before the redirect had problems
        if (!model.containsAttribute("errors")) {
            model.addAttribute("errors", List.of());
        }
        return "student/plan";
    }

    @PostMapping("/student/plan/terms")
    public String addTerm(@RequestParam(defaultValue = "") String year,
                          @RequestParam(defaultValue = "") String season,
                          @RequestParam(defaultValue = "") String planId,
                          HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        Long viewedPlanId = parsePlanId(planId);
        Integer parsedYear = parseYear(year);
        if (parsedYear == null) {
            return backToPlan(PlanChange.rejected("Choose a year."), viewedPlanId, redirectAttributes);
        }
        PlanChange change = planService.addTerm(student.get(), viewedPlanId, parsedYear, season);
        return backToPlan(change, viewedPlanId, redirectAttributes);
    }

    @PostMapping("/student/plan/terms/{termId}/remove")
    public String removeTerm(@PathVariable Long termId, @RequestParam(defaultValue = "") String planId,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlan(planService.removeTerm(student.get(), termId), parsePlanId(planId), redirectAttributes);
    }

    @PostMapping("/student/plan/terms/{termId}/courses")
    public String addCourse(@PathVariable Long termId, @RequestParam(defaultValue = "") String code,
                            @RequestParam(defaultValue = "") String planId,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        PlanChange change = planService.addCourse(student.get(), termId, code);
        // On an error the code goes back into the box, so fixing one digit doesn't mean typing it all again
        if (!change.isSaved()) {
            redirectAttributes.addFlashAttribute("typedCode", code);
        }
        return backToPlan(change, parsePlanId(planId), redirectAttributes);
    }

    @PostMapping("/student/plan/courses/{courseId}/remove")
    public String removeCourse(@PathVariable Long courseId, @RequestParam(defaultValue = "") String planId,
                               HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlan(planService.removeCourse(student.get(), courseId), parsePlanId(planId), redirectAttributes);
    }

    // Says what the change did, or shows the problems that stopped it.
    // A change inside a term jumps back to that term (#term-5) and shows its message there, so the student
    // doesn't have to scroll back down to keep going.
    // viewedPlanId only decides which page to go back to. That page checks the plan is the student's own
    private static String backToPlan(PlanChange change, Long viewedPlanId, RedirectAttributes redirectAttributes) {
        if (change.isSaved()) {
            redirectAttributes.addFlashAttribute("message", change.message());
        } else {
            redirectAttributes.addFlashAttribute("errors", change.problems());
        }
        String page = viewedPlanId == null ? "redirect:/student/plan" : "redirect:/student/plans/" + viewedPlanId;
        if (change.termId() == null) {
            return page;
        }
        redirectAttributes.addFlashAttribute("changedTermId", change.termId());
        return page + "#term-" + change.termId();
    }

    // Null when the year is blank or not a number
    private static Integer parseYear(String year) {
        try {
            return Integer.parseInt(year.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Null when the form came from /student/plan, which has no plan id.
    // Text that isn't a number becomes an id no plan has, so the change is turned down as not the student's
    private static Long parsePlanId(String planId) {
        if (planId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(planId.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
