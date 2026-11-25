package cmpt276.group16.controllers;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import cmpt276.group16.models.ScheduledCourse;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.services.EligibilityService;
import cmpt276.group16.services.NextSemesterScheduleService;
import cmpt276.group16.services.PlanPrerequisites;
import cmpt276.group16.services.PlanService;
import cmpt276.group16.services.PlanService.TermPlan;
import cmpt276.group16.services.UnitTotals;
import cmpt276.group16.transcript.Terms;
import jakarta.servlet.http.HttpSession;

// #71: an advisor's read-only view of one assigned student, to prepare for an advising appointment:
// their course record, eligible courses, next semester's schedule and main plan.
// The Graduation Report joins this page with #69.
// AuthInterceptor only lets advisors reach /advisor/**, so students and IT managers never get here
@Controller
public class AdvisorStudentController {

    private final AssignedStudent assignedStudent;
    private final TranscriptCourseRepository transcriptCourseRepository;
    private final EligibilityService eligibilityService;
    private final NextSemesterScheduleService scheduleService;
    private final PlanService planService;
    private final PlanPrerequisites planPrerequisites;

    public AdvisorStudentController(AssignedStudent assignedStudent,
            TranscriptCourseRepository transcriptCourseRepository, EligibilityService eligibilityService,
            NextSemesterScheduleService scheduleService, PlanService planService,
            PlanPrerequisites planPrerequisites) {
        this.assignedStudent = assignedStudent;
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.eligibilityService = eligibilityService;
        this.scheduleService = scheduleService;
        this.planService = planService;
        this.planPrerequisites = planPrerequisites;
    }

    /** GET /advisor/students/5 : the overview of student 5, if they're assigned to the logged-in advisor. */
    @GetMapping("/advisor/students/{studentId}")
    public String showStudent(@PathVariable Long studentId, HttpSession session, Model model) {
        // A 403 unless the student is assigned to this advisor, before anything about them is loaded
        StudentProfile student = assignedStudent.find(session, studentId);

        // The same course record My courses shows: terms oldest first, courses in the order they were saved
        List<TranscriptCourse> courses = transcriptCourseRepository.findByStudentOrderByIdAsc(student);
        Map<String, List<TranscriptCourse>> coursesByTerm = courses.stream().collect(Collectors.groupingBy(
                TranscriptCourse::getTerm, () -> new TreeMap<>(Terms.OLDEST_FIRST), Collectors.toList()));

        List<ScheduledCourse> scheduledCourses = scheduleService.coursesFor(student);
        List<TermPlan> termPlans = planService.termPlans(student);

        model.addAttribute("student", student);
        model.addAttribute("coursesByTerm", coursesByTerm);
        model.addAttribute("units", UnitTotals.of(courses));
        model.addAttribute("eligibility", eligibilityService.forNextSemester(student));
        model.addAttribute("scheduledCourses", scheduledCourses);
        model.addAttribute("scheduleUnits", scheduleService.totalUnits(scheduledCourses));
        model.addAttribute("unitLimit", scheduleService.unitLimitFor(student));
        model.addAttribute("conflicts", scheduleService.conflicts(scheduledCourses));
        model.addAttribute("termPlans", termPlans);
        model.addAttribute("missing", planPrerequisites.missingFor(student, termPlans));
        return "advisor/student";
    }
}