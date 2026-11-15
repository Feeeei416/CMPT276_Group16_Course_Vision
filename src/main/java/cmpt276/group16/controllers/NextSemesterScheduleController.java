package cmpt276.group16.controllers;

import cmpt276.group16.services.NextSemesterScheduleService;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;


import cmpt276.group16.models.StudentProfile;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NextSemesterScheduleController{

    private final CurrentStudent currentStudent;
    private final NextSemesterScheduleService scheduleService;

    public NextSemesterScheduleController(CurrentStudent currentStudent, NextSemesterScheduleService scheduleService) {
        this.currentStudent = currentStudent;
        this.scheduleService = scheduleService;
    }

    @GetMapping("/student/schedule/next-semester")
    public String showSchedule(HttpSession session, Model model) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        model.addAttribute("courses", scheduleService.coursesFor(student.get()));
        if (!model.containsAttribute("errors")) {
            model.addAttribute("errors", List.of());
        }
        return "student/next-semester-schedule";
    }

    @PostMapping("/students/schedule/next-semester/courses/{id}/remove")
    public String removeCourse(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        List<String> problems = scheduleService.removeCourse(student.get(), id);
        return backToSchedule(problems, "Course removed.", redirectAttributes);
    }

    private static String backToSchedule(List<String> problems, String success,
            RedirectAttributes redirectAttributes) {
        if (problems.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", success);
        } else {
            redirectAttributes.addFlashAttribute("errors", problems);
        }
        return "redirect:/student/schedule/next-semester";
    }

    @PostMapping("/student/schedule/next-semester/courses")
    public String addCourse(@RequestParam(required = false) Long offeringId,
            @RequestParam(required = false) Long primarySectionId,
            @RequestParam(required = false) Long attachedSectionId,
            HttpSession session, RedirectAttributes redirectAttributes) {
        Optional<StudentProfile> student = currentStudent.find(session);
        if (student.isEmpty()) {
            return "redirect:/login";
        }
        List<String> problems = scheduleService.addCourse(student.get(),
                offeringId, primarySectionId, attachedSectionId);
        return backToSchedule(problems, "Course added.", redirectAttributes);
    }
}