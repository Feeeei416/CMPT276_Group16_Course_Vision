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

import cmpt276.group16.services.PlanChange;
import cmpt276.group16.services.PlanListService;
import jakarta.servlet.http.HttpSession;

// All my plans: every plan a student has, such as a lighter load or one with a co-op term.
// An assigned advisor gets the same page for a student under /advisor/students/{studentId} (#73), so every
// handler has both paths. studentId is null on the student's own pages. See PlanOwner.
// Every change redirects back to the page, so refreshing it doesn't send the form again
@Controller
public class PlanListController {

    private static final String ADVISOR = "/advisor/students/{studentId}";

    private final PlanOwner.Finder planOwners;
    private final PlanListService planListService;

    public PlanListController(PlanOwner.Finder planOwners, PlanListService planListService) {
        this.planOwners = planOwners;
        this.planListService = planListService;
    }

    @GetMapping({"/student/plans", ADVISOR + "/plans"})
    public String showPlans(@PathVariable(required = false) Long studentId, HttpSession session, Model model) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("owner", owner.get());
        model.addAttribute("plans", planListService.plans(owner.get().student()));
        // Already set when the change before the redirect had problems
        if (!model.containsAttribute("errors")) {
            model.addAttribute("errors", List.of());
        }
        return "student/plans";
    }

    // copyFrom is blank for an empty plan, or the id of the plan to copy
    @PostMapping({"/student/plans", ADVISOR + "/plans"})
    public String createPlan(@PathVariable(required = false) Long studentId,
                             @RequestParam(defaultValue = "") String name,
                             @RequestParam(defaultValue = "") String copyFrom,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        PlanChange change = planListService.create(owner.get().student(), name, parseCopyFrom(copyFrom));
        // On an error the name and choice go back into the form, so the student doesn't have to start again
        if (!change.isSaved()) {
            redirectAttributes.addFlashAttribute("typedName", name);
            redirectAttributes.addFlashAttribute("chosenCopyFrom", copyFrom);
        }
        return backToPlans(owner.get(), change, redirectAttributes);
    }

    @PostMapping({"/student/plans/{planId}/rename", ADVISOR + "/plans/{planId}/rename"})
    public String renamePlan(@PathVariable(required = false) Long studentId, @PathVariable Long planId,
                             @RequestParam(defaultValue = "") String name,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlans(owner.get(), planListService.rename(owner.get().student(), planId, name),
                redirectAttributes);
    }

    @PostMapping({"/student/plans/{planId}/delete", ADVISOR + "/plans/{planId}/delete"})
    public String deletePlan(@PathVariable(required = false) Long studentId, @PathVariable Long planId,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlans(owner.get(), planListService.delete(owner.get().student(), planId), redirectAttributes);
    }

    @PostMapping({"/student/plans/{planId}/main", ADVISOR + "/plans/{planId}/main"})
    public String makeMain(@PathVariable(required = false) Long studentId, @PathVariable Long planId,
                           HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<PlanOwner> owner = planOwners.find(session, studentId);
        if (owner.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlans(owner.get(), planListService.makeMain(owner.get().student(), planId), redirectAttributes);
    }

    private static String backToPlans(PlanOwner owner, PlanChange change, RedirectAttributes redirectAttributes) {
        if (change.isSaved()) {
            redirectAttributes.addFlashAttribute("message", change.message());
        } else {
            redirectAttributes.addFlashAttribute("errors", change.problems());
        }
        return "redirect:" + owner.basePath() + "/plans";
    }

    // Null for an empty plan. Text that isn't a number becomes an id no plan has, so it's rejected as not yours
    private static Long parseCopyFrom(String copyFrom) {
        if (copyFrom.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(copyFrom.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
