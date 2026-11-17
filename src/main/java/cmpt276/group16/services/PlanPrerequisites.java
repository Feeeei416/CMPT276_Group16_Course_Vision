package cmpt276.group16.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.RequisiteRuleRepository;
import cmpt276.group16.models.RuleKind;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.services.PlanService.TermPlan;

// Flags the courses in a student's plan whose prerequisites won't be met by the term they're planned for (#44).
// A prerequisite counts as met if it's in the student's record (courses in progress included) or planned for
// an earlier term. A course planned for the same term only counts when the Calendar says it may be taken
// concurrently
@Service
public class PlanPrerequisites {

    private final TranscriptCourseRepository transcriptCourseRepository;
    private final RequisiteRuleRepository requisiteRuleRepository;

    public PlanPrerequisites(TranscriptCourseRepository transcriptCourseRepository,
            RequisiteRuleRepository requisiteRuleRepository) {
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.requisiteRuleRepository = requisiteRuleRepository;
    }

    // What each planned course is missing, by PlannedCourse id, for example ["Needs CMPT 225 with C- or better."].
    // Courses whose prerequisites are met aren't in the map.
    // termPlans has to be oldest first, the way PlanService.termPlans returns it
    @Transactional(readOnly = true)
    public Map<Long, List<String>> missingFor(StudentProfile student, List<TermPlan> termPlans) {
        List<TranscriptCourse> record = transcriptCourseRepository.findByStudentOrderByIdAsc(student);
        Map<Long, List<String>> missing = new HashMap<>();
        List<PlannedCourse> earlier = new ArrayList<>();
        for (TermPlan termPlan : termPlans) {
            for (PlannedCourse planned : termPlan.courses()) {
                List<RequisiteRule> rules = requisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc(
                        planned.getCourse().getId(), RuleKind.PREREQUISITE);
                // The course itself is left out, so it can't count towards its own prerequisites
                List<PlannedCourse> sameTerm = termPlan.courses().stream().filter(other -> other != planned).toList();
                PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, record, List.copyOf(earlier), sameTerm);
                if (!result.met()) {
                    missing.put(planned.getId(), result.missing());
                }
            }
            earlier.addAll(termPlan.courses());
        }
        return missing;
    }
}
