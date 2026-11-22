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
import cmpt276.group16.services.NextSemesterScheduleService;
import cmpt276.group16.services.PlanChange;
import cmpt276.group16.services.ScheduleListService;
import jakarta.servlet.http.HttpSession;

// My schedules: every schedule a student has for next semester, such as one with a morning lab and one with an
// afternoon lab. Every change redirects back to the page, so refreshing it doesn't send the form again
@Controller
public class ScheduleListController {

    private final CurrentStudent currentStudent;
    private final ScheduleListService scheduleListService;
    private final NextSemesterScheduleService scheduleService;

    public ScheduleListController(CurrentStudent currentStudent, ScheduleListService scheduleListService,
            NextSemesterScheduleService scheduleService) {
        this.currentStudent = currentStudent;
        this.scheduleListService = scheduleListService;
        this.scheduleService = scheduleService;
    }

    @GetMapping("/student/schedules")
    public String showSchedules(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("schedules", scheduleListService.schedules(student.get()));
        // Null until next semester's courses are imported, so the page can say why there's nothing to make
        model.addAttribute("term", scheduleService.nextSemesterTerm().orElse(null));
        // Already set when the change before the redirect had problems
        if (!model.containsAttribute("errors")) {
            model.addAttribute("errors", List.of());
        }
        return "student/schedules";
    }

    // copyFrom is blank for an empty schedule, or the id of the schedule to copy
    @PostMapping("/student/schedules")
    public String createSchedule(@RequestParam(defaultValue = "") String name,
                                 @RequestParam(defaultValue = "") String copyFrom,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        PlanChange change = scheduleListService.create(student.get(), name, parseCopyFrom(copyFrom));
        // On an error the name and choice go back into the form, so the student doesn't have to start again
        if (!change.isSaved()) {
            redirectAttributes.addFlashAttribute("typedName", name);
            redirectAttributes.addFlashAttribute("chosenCopyFrom", copyFrom);
        }
        return backToSchedules(change, redirectAttributes);
    }

    @PostMapping("/student/schedules/{scheduleId}/rename")
    public String renameSchedule(@PathVariable Long scheduleId, @RequestParam(defaultValue = "") String name,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToSchedules(scheduleListService.rename(student.get(), scheduleId, name), redirectAttributes);
    }

    @PostMapping("/student/schedules/{scheduleId}/delete")
    public String deleteSchedule(@PathVariable Long scheduleId, HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToSchedules(scheduleListService.delete(student.get(), scheduleId), redirectAttributes);
    }

    @PostMapping("/student/schedules/{scheduleId}/main")
    public String makeMain(@PathVariable Long scheduleId, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        return backToSchedules(scheduleListService.makeMain(student.get(), scheduleId), redirectAttributes);
    }

    private static String backToSchedules(PlanChange change, RedirectAttributes redirectAttributes) {
        if (change.isSaved()) {
            redirectAttributes.addFlashAttribute("message", change.message());
        } else {
            redirectAttributes.addFlashAttribute("errors", change.problems());
        }
        return "redirect:/student/schedules";
    }

    // Null for an empty schedule. Text that isn't a number becomes an id no schedule has, so it's rejected
    // as not the student's
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
