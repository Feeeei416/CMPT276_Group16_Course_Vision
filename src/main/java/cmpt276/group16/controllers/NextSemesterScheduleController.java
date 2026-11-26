package cmpt276.group16.controllers;

import cmpt276.group16.services.NextSemesterScheduleService;
import cmpt276.group16.services.PlanRules;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;

import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.Section;
import cmpt276.group16.models.StudentProfile;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NextSemesterScheduleController {

    private final CurrentStudent currentStudent;
    private final NextSemesterScheduleService scheduleService;

    public NextSemesterScheduleController(CurrentStudent currentStudent, NextSemesterScheduleService scheduleService) {
        this.currentStudent = currentStudent;
        this.scheduleService = scheduleService;
    }

    @PostMapping("/student/schedule/next-semester/courses/{id}/remove")
    public String removeCourse(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        List<String> problems = scheduleService.removeCourse(student.get(), id);
        return backToSchedule(problems, "Course removed.", null, redirectAttributes);
    }

    private static String backToSchedule(List<String> problems, String success, String code,
            RedirectAttributes redirectAttributes) {
        if (problems.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", success);
        } else {
            redirectAttributes.addFlashAttribute("errors", problems);
            if (code != null && !code.isBlank()) {
                redirectAttributes.addAttribute("code", code);
            }
        }
        return "redirect:/student/schedule/next-semester";
    }

    @PostMapping("/student/schedule/next-semester/courses")
    public String addCourse(@RequestParam(required = false) Long offeringId,
            @RequestParam(required = false) Long primarySectionId,
            @RequestParam(required = false) Long attachedSectionId,
            @RequestParam(required = false) String code,
            HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        List<String> problems = scheduleService.addCourse(student.get(),
                offeringId, primarySectionId, attachedSectionId);
        return backToSchedule(problems, "Course added.", code, redirectAttributes);
    }

    @GetMapping("/student/schedule/next-semester")
    public String showSchedule(@RequestParam(required = false) String code,
            HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("courses", scheduleService.coursesFor(student.get()));
        model.addAttribute("meetingsByDay", scheduleService.meetingsByDay(student.get()));
        model.addAttribute("timetableBlocks", scheduleService.timetableBlocks(student.get()));
        model.addAttribute("gridHours", scheduleService.gridHours(student.get()));
        model.addAttribute("offerings", scheduleService.offeredNextSemester());
        model.addAttribute("totalUnits", scheduleService.totalUnits(student.get()));
        model.addAttribute("unitLimit", scheduleService.unitLimitFor(student.get()));
        model.addAttribute("conflicts", scheduleService.conflicts(student.get()));
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
}