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
import cmpt276.group16.models.TermKind;
import cmpt276.group16.services.PlanChange;
import cmpt276.group16.services.PlanListService;
import cmpt276.group16.services.PlanPrerequisites;
import cmpt276.group16.services.PlanService;
import cmpt276.group16.services.PlanService.TermPlan;
import jakarta.servlet.http.HttpSession;

// My plan: the courses a student plans to take in the terms after next semester.
// /student/plan shows the main plan, and /student/plans/5 shows any one of the student's plans on the same page.
// An assigned advisor gets the same page for a student under /advisor/students/{studentId} (#73), so every
// handler has both paths. studentId is null on the student's own pages. See PlanOwner.
// Every change redirects back to the page it came from, so refreshing it doesn't send the form again
@Controller
public class PlanController {

    private static final String ADVISOR = "/advisor/students/{studentId}";

    private final PlanOwner.Finder planOwners;
    private final PlanService planService;
    private final PlanListService planListService;
    private final PlanPrerequisites planPrerequisites;

    public PlanController(PlanOwner.Finder planOwners, PlanService planService, PlanListService planListService,
            PlanPrerequisites planPrerequisites) {
        this.planOwners = planOwners;
        this.planService = planService;
        this.planListService = planListService;
        this.planPrerequisites = planPrerequisites;
    }

    @GetMapping({"/student/plan", ADVISOR + "/plan"})
    public String showPlan(@PathVariable(required = false) Long studentId, HttpSession session, Model model) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        return showPlanPage(owner.get(), planService.mainPlan(owner.get().student()).orElse(null), null, model);
    }

    @GetMapping({"/student/plans/{planId}", ADVISOR + "/plans/{planId}"})
    public String showOnePlan(@PathVariable(required = false) Long studentId, @PathVariable Long planId,
                              HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        Optional<Plan> plan = planListService.find(owner.get().student(), planId);
        if (plan.isEmpty()) {
            redirectAttributes.addFlashAttribute("errors", List.of("That plan isn't one of yours."));
            return "redirect:" + owner.get().basePath() + "/plans";
        }
        return showPlanPage(owner.get(), plan.get(), planId, model);
    }

    // plan is null when the student has no plan yet. viewedPlanId is set when the page was opened as
    // /student/plans/5, so its forms send it back and each change returns to that plan
    private String showPlanPage(PlanOwner owner, Plan plan, Long viewedPlanId, Model model) {
        List<TermPlan> termPlans = plan == null ? List.of() : planService.termPlans(plan);
        model.addAttribute("owner", owner);
        model.addAttribute("plan", plan);
        model.addAttribute("viewedPlanId", viewedPlanId);
        model.addAttribute("termPlans", termPlans);
        // Worked out again on every visit, so adding or moving a missing course clears its flag
        model.addAttribute("missing", planPrerequisites.missingFor(owner.student(), termPlans));
        model.addAttribute("nextSemester", planService.nextSemester());
        model.addAttribute("years", planService.plannableYears());
        model.addAttribute("seasons", Season.values());
        model.addAttribute("kinds", TermKind.values());
        // Already set when the change before the redirect had problems
        if (!model.containsAttribute("errors")) {
            model.addAttribute("errors", List.of());
        }
        return "student/plan";
    }

    @PostMapping({"/student/plan/terms", ADVISOR + "/plan/terms"})
    public String addTerm(@PathVariable(required = false) Long studentId,
                          @RequestParam(defaultValue = "") String year,
                          @RequestParam(defaultValue = "") String season,
                          @RequestParam(defaultValue = "") String kind,
                          @RequestParam(defaultValue = "") String planId,
                          HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        Long viewedPlanId = parsePlanId(planId);
        Integer parsedYear = parseYear(year);
        if (parsedYear == null) {
            return backToPlan(owner.get(), PlanChange.rejected("Choose a year."), viewedPlanId, redirectAttributes);
        }
        PlanChange change = planService.addTerm(owner.get().student(), viewedPlanId, parsedYear, season, kind);
        return backToPlan(owner.get(), change, viewedPlanId, redirectAttributes);
    }

    @PostMapping({"/student/plan/terms/{termId}/remove", ADVISOR + "/plan/terms/{termId}/remove"})
    public String removeTerm(@PathVariable(required = false) Long studentId, @PathVariable Long termId,
                             @RequestParam(defaultValue = "") String planId,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        PlanChange change = planService.removeTerm(owner.get().student(), termId);
        return backToPlan(owner.get(), change, parsePlanId(planId), redirectAttributes);
    }

    @PostMapping({"/student/plan/terms/{termId}/courses", ADVISOR + "/plan/terms/{termId}/courses"})
    public String addCourse(@PathVariable(required = false) Long studentId, @PathVariable Long termId,
                            @RequestParam(defaultValue = "") String code,
                            @RequestParam(defaultValue = "") String planId,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        PlanChange change = planService.addCourse(owner.get().student(), termId, code);
        // On an error the code goes back into the box, so fixing one digit doesn't mean typing it all again
        if (!change.isSaved()) {
            redirectAttributes.addFlashAttribute("typedCode", code);
        }
        return backToPlan(owner.get(), change, parsePlanId(planId), redirectAttributes);
    }

    @PostMapping({"/student/plan/courses/{courseId}/remove", ADVISOR + "/plan/courses/{courseId}/remove"})
    public String removeCourse(@PathVariable(required = false) Long studentId, @PathVariable Long courseId,
                               @RequestParam(defaultValue = "") String planId,
                               HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        PlanChange change = planService.removeCourse(owner.get().student(), courseId);
        return backToPlan(owner.get(), change, parsePlanId(planId), redirectAttributes);
    }

    // Says what the change did, or shows the problems that stopped it.
    // A change inside a term jumps back to that term (#term-5) and shows its message there, so the student
    // doesn't have to scroll back down to keep going.
    // viewedPlanId only decides which page to go back to. That page checks the plan is the student's own
    private static String backToPlan(PlanOwner owner, PlanChange change, Long viewedPlanId,
                                     RedirectAttributes redirectAttributes) {
        if (change.isSaved()) {
            redirectAttributes.addFlashAttribute("message", change.message());
        } else {
            redirectAttributes.addFlashAttribute("errors", change.problems());
        }
        String page = "redirect:" + owner.basePath()
                + (viewedPlanId == null ? "/plan" : "/plans/" + viewedPlanId);
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
