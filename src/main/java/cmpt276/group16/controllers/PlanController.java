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

import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.services.PlanService;
import jakarta.servlet.http.HttpSession;

// My plan: the courses a student plans to take in the terms after next semester.
// Every change redirects back to the page, so refreshing it doesn't send the form again
@Controller
public class PlanController {

    private final CurrentStudent currentStudent;
    private final PlanService planService;

    public PlanController(CurrentStudent currentStudent, PlanService planService) {
        this.currentStudent = currentStudent;
        this.planService = planService;
    }

    @GetMapping("/student/plan")
    public String showPlan(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("termPlans", planService.termPlans(student.get()));
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
                          HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        Integer parsedYear = parseYear(year);
        if (parsedYear == null) {
            return backToPlan(List.of("Choose a year."), redirectAttributes);
        }
        return backToPlan(planService.addTerm(student.get(), parsedYear, season), redirectAttributes);
    }

    @PostMapping("/student/plan/terms/{termId}/remove")
    public String removeTerm(@PathVariable Long termId, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlan(planService.removeTerm(student.get(), termId), redirectAttributes);
    }

    @PostMapping("/student/plan/terms/{termId}/courses")
    public String addCourse(@PathVariable Long termId, @RequestParam(defaultValue = "") String code,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlan(planService.addCourse(student.get(), termId, code), redirectAttributes);
    }

    @PostMapping("/student/plan/courses/{courseId}/remove")
    public String removeCourse(@PathVariable Long courseId, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToPlan(planService.removeCourse(student.get(), courseId), redirectAttributes);
    }

    // Shows the problems at the top of the page, or a saved message if there were none
    private static String backToPlan(List<String> problems, RedirectAttributes redirectAttributes) {
        if (problems.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Your plan was saved.");
        } else {
            redirectAttributes.addFlashAttribute("errors", problems);
        }
        return "redirect:/student/plan";
    }

    // Null when the year is blank or not a number
    private static Integer parseYear(String year) {
        try {
            return Integer.parseInt(year.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
