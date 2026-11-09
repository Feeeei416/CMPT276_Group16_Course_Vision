package cmpt276.group16.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.ui.Model;

import cmpt276.group16.models.Grades;
import cmpt276.group16.transcript.CourseInput;
import cmpt276.group16.transcript.Terms;

// What fragments/course-form needs, shared by the Add course form on My courses and the Edit course page
final class CourseForms {

    private CourseForms() {
    }

    // form holds the typed values (or the course being edited), errors the problems to show above it
    static void addTo(Model model, Map<String, String> form, List<String> errors) {
        model.addAttribute("form", form);
        model.addAttribute("formErrors", errors);
        model.addAttribute("seasons", Terms.SEASONS);
        model.addAttribute("letterGrades", Grades.LETTER);
        model.addAttribute("otherGrades", Grades.OTHER);
        model.addAttribute("transferGrade", Grades.TRANSFER);
        model.addAttribute("minYear", CourseInput.MIN_YEAR);
        model.addAttribute("maxYear", CourseInput.maxYear());
    }

    // For messages like "Added CMPT 120 to 2025 Fall."
    static String name(CourseInput course) {
        return course.subject() + " " + course.number();
    }

    // Shown when the course would be a second copy of one already in the record
    static String alreadyInRecord(CourseInput course) {
        return name(course) + " in " + course.term() + " is already in your record.";
    }
}
