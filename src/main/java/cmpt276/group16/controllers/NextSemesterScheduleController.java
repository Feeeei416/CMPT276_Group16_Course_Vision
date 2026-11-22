package cmpt276.group16.controllers;

import cmpt276.group16.services.NextSemesterScheduleService;
import cmpt276.group16.services.PlanRules;
import cmpt276.group16.services.ScheduleListService;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;

import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.NextSemesterSchedule;
import cmpt276.group16.models.ScheduledCourse;
import cmpt276.group16.models.Section;
import cmpt276.group16.models.StudentProfile;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// My schedule: the courses and sections a student picks for next semester.
// /student/schedule/next-semester shows the main schedule, and /student/schedules/5 shows any one of the
// student's schedules on the same page. Every change redirects back to the page it came from
@Controller
public class NextSemesterScheduleController {

    private final CurrentStudent currentStudent;
    private final NextSemesterScheduleService scheduleService;
    private final ScheduleListService scheduleListService;

    public NextSemesterScheduleController(CurrentStudent currentStudent, NextSemesterScheduleService scheduleService,
            ScheduleListService scheduleListService) {
        this.currentStudent = currentStudent;
        this.scheduleService = scheduleService;
        this.scheduleListService = scheduleListService;
    }

    // scheduleId only decides which page to go back to. The course id already says which schedule it's in
    @PostMapping("/student/schedule/next-semester/courses/{id}/remove")
    public String removeCourse(@PathVariable Long id, @RequestParam(defaultValue = "") String scheduleId,
            HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        List<String> problems = scheduleService.removeCourse(student.get(), id);
        return backToSchedule(problems, "Course removed.", null, parseScheduleId(scheduleId), redirectAttributes);
    }

    // Goes back to the schedule the change came from: the main one when viewedScheduleId is null.
    // That page checks the schedule is the student's own
    private static String backToSchedule(List<String> problems, String success, String code,
            Long viewedScheduleId, RedirectAttributes redirectAttributes) {
        if (problems.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", success);
        } else {
            redirectAttributes.addFlashAttribute("errors", problems);
            if (code != null && !code.isBlank()) {
                redirectAttributes.addAttribute("code", code);
            }
        }
        return "redirect:" + pageFor(viewedScheduleId);
    }

    private static String pageFor(Long viewedScheduleId) {
        return viewedScheduleId == null ? "/student/schedule/next-semester" : "/student/schedules/" + viewedScheduleId;
    }

    @PostMapping("/student/schedule/next-semester/courses")
    public String addCourse(@RequestParam(required = false) Long offeringId,
            @RequestParam(required = false) Long primarySectionId,
            @RequestParam(required = false) Long attachedSectionId,
            @RequestParam(required = false) String code,
            @RequestParam(defaultValue = "") String scheduleId,
            HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        Long viewedScheduleId = parseScheduleId(scheduleId);
        List<String> problems = scheduleService.addCourse(student.get(), viewedScheduleId,
                offeringId, primarySectionId, attachedSectionId);
        return backToSchedule(problems, "Course added.", code, viewedScheduleId, redirectAttributes);
    }

    @GetMapping("/student/schedule/next-semester")
    public String showSchedule(@RequestParam(required = false) String code,
            HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        NextSemesterSchedule main = scheduleService.mainSchedule(student.get()).orElse(null);
        return showSchedulePage(student.get(), main, null, code, model);
    }

    @GetMapping("/student/schedules/{scheduleId}")
    public String showOneSchedule(@PathVariable Long scheduleId, @RequestParam(required = false) String code,
            HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        Optional<NextSemesterSchedule> schedule = scheduleListService.find(student.get(), scheduleId);
        if (schedule.isEmpty()) {
            redirectAttributes.addFlashAttribute("errors", List.of("That schedule isn't one of yours."));
            return "redirect:/student/schedules";
        }
        return showSchedulePage(student.get(), schedule.get(), scheduleId, code, model);
    }

    // schedule is null when the student has no schedule yet. viewedScheduleId is set when the page was opened
    // as /student/schedules/5, so its forms send it back and each change returns to that schedule
    private String showSchedulePage(StudentProfile student, NextSemesterSchedule schedule, Long viewedScheduleId,
            String code, Model model) {
        List<ScheduledCourse> courses = schedule == null ? List.of() : scheduleService.coursesFor(schedule);
        model.addAttribute("schedule", schedule);
        model.addAttribute("viewedScheduleId", viewedScheduleId);
        model.addAttribute("pageUrl", pageFor(viewedScheduleId));
        model.addAttribute("courses", courses);
        model.addAttribute("meetingsByDay", scheduleService.meetingsByDay(courses));
        model.addAttribute("offerings", scheduleService.offeredNextSemester());
        model.addAttribute("totalUnits", scheduleService.totalUnits(courses));
        model.addAttribute("unitLimit", scheduleService.unitLimitFor(student));
        model.addAttribute("conflicts", scheduleService.conflicts(courses));
        if (!model.containsAttribute("errors")) {
            model.addAttribute("errors", List.of());
        }

        // After "Choose": show the chosen course's lectures and labs or tutorials
        if (code != null && !code.isBlank()) {
            Optional<CourseOffering> chosen = scheduleService.findOffering(code);
            if (chosen.isEmpty()) {
                Optional<PlanRules.CourseCode> parsed = PlanRules.parseCourseCode(code);
                String error = parsed.isPresent()
                        ? parsed.get().subject() + " " + parsed.get().number() + " doesn't run next semester."
                        : "Enter a course code like CMPT 225.";
                model.addAttribute("errors", List.of(error));
            } else {
                List<Section> sections = scheduleService.sectionsOf(chosen.get());
                model.addAttribute("chosen", chosen.get());
                model.addAttribute("lectures", sections.stream().filter(Section::isPrimarySection).toList());
                model.addAttribute("attachedSections", sections.stream().filter(s -> !s.isPrimarySection()).toList());
            }
        }
        return "student/next-semester-schedule";
    }

    // Null when the form came from /student/schedule/next-semester, which has no schedule id.
    // Text that isn't a number becomes an id no schedule has, so the change is turned down as not the student's
    private static Long parseScheduleId(String scheduleId) {
        if (scheduleId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(scheduleId.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
