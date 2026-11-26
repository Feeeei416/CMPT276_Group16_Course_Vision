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

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.services.PlanChange;
import cmpt276.group16.services.PlanListService;
import jakarta.servlet.http.HttpSession;

// All my plans: every plan a student has, such as a lighter load or one with a co-op term.
// Every change redirects back to the page, so refreshing it doesn't send the form again
@Controller
public class PlanListController {

    private final CurrentStudent currentStudent;
    private final PlanListService planListService;

    public PlanListController(CurrentStudent currentStudent, PlanListService planListService) {
        this.currentStudent = currentStudent;
        this.planListService = planListService;
    }

    @GetMapping("/student/plans")
    public String showPlans(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("plans", planListService.plans(student.get()));
        // Already set when the change before the redirect had problems
        if (!model.containsAttribute("errors")) {
            model.addAttribute("errors", List.of());
        }
        return "student/plans";
    }

    // copyFrom is blank for an empty plan, or the id of the plan to copy
    @PostMapping("/student/plans")
    public String createPlan(@RequestParam(defaultValue = "") String name,
                             @RequestParam(defaultValue = "") String copyFrom,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        PlanChange change = planListService.create(student.get(), name, parseCopyFrom(copyFrom));
        // On an error the name and choice go back into the form, so the student doesn't have to start again
        if (!change.isSaved()) {
            redirectAttributes.addFlashAttribute("typedName", name);
            redirectAttributes.addFlashAttribute("chosenCopyFrom", copyFrom);
        }
        return backToPlans(change, redirectAttributes);
    }

    @PostMapping("/student/plans/{planId}/rename")
    public String renamePlan(@PathVariable Long planId, @RequestParam(defaultValue = "") String name,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlans(planListService.rename(student.get(), planId, name), redirectAttributes);
    }

    @PostMapping("/student/plans/{planId}/delete")
    public String deletePlan(@PathVariable Long planId, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlans(planListService.delete(student.get(), planId), redirectAttributes);
    }

    @PostMapping("/student/plans/{planId}/main")
    public String makeMain(@PathVariable Long planId, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlans(planListService.makeMain(student.get(), planId), redirectAttributes);
    }

    private static String backToPlans(PlanChange change, RedirectAttributes redirectAttributes) {
        if (change.isSaved()) {
            redirectAttributes.addFlashAttribute("message", change.message());
        } else {
            redirectAttributes.addFlashAttribute("errors", change.problems());
        }
        return "redirect:/student/plans";
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
