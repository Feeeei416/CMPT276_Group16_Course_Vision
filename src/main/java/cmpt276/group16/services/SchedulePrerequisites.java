package cmpt276.group16.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.RequisiteRuleRepository;
import cmpt276.group16.models.RuleKind;
import cmpt276.group16.models.ScheduledCourse;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.services.PrerequisiteCheck.Result;
import cmpt276.group16.services.PrerequisiteCheck.Upcoming;

// Flags the courses in a next-semester schedule whose prerequisites aren't met (#74), the same way My plan does (#44).
// A prerequisite counts as met if it's in the student's record from before that term, including a course still in
// progress this term, which comes with a note to confirm it. Another course in the same schedule only counts when
// the Calendar says the prerequisite may be taken concurrently
@Service
public class SchedulePrerequisites {

    private final TranscriptCourseRepository transcriptCourseRepository;
    private final RequisiteRuleRepository requisiteRuleRepository;

    public SchedulePrerequisites(TranscriptCourseRepository transcriptCourseRepository,
            RequisiteRuleRepository requisiteRuleRepository) {
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.requisiteRuleRepository = requisiteRuleRepository;
    }

    // The prerequisite check for each course in the schedule, by ScheduledCourse id: what it's missing, and what to
    // confirm. Courses with nothing to show aren't in the map. term is the schedule's term, like 2027 Spring
    @Transactional(readOnly = true)
    public Map<Long, Result> resultsFor(StudentProfile student, List<ScheduledCourse> courses, Term term) {
        if (courses.isEmpty()) {
            return Map.of();
        }
        // A course can be added by hand for next semester or later, and it doesn't count until then
        List<TranscriptCourse> recordBefore = PrerequisiteCheck.takenBefore(
                transcriptCourseRepository.findByStudentOrderByIdAsc(student),
                PlanTerm.nameOf(term.getTermYear(), term.getSeason()));

        Map<Long, Result> results = new HashMap<>();
        for (ScheduledCourse scheduled : courses) {
            List<RequisiteRule> rules = requisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc(
                    scheduled.getOffering().getCourse().getId(), RuleKind.PREREQUISITE);
            // The course itself is left out, so it can't count towards its own prerequisites
            List<Upcoming> sameSchedule = courses.stream()
                    .filter(other -> other != scheduled)
                    .map(SchedulePrerequisites::inSchedule)
                    .toList();
            Result result = PrerequisiteCheck.checkUpcoming(rules, recordBefore, List.of(), sameSchedule);
            if (!result.missing().isEmpty() || !result.toConfirm().isEmpty()) {
                results.put(scheduled.getId(), result);
            }
        }
        return results;
    }

    // Another course in the same schedule: "CMPT 225 is in your schedule too."
    private static Upcoming inSchedule(ScheduledCourse scheduled) {
        Course course = scheduled.getOffering().getCourse();
        return new Upcoming(course.getSubject() + " " + course.getNumber(), course.getUnits(),
                PlanRules.isCoopCourse(course.getSubject(), course.getNumber()), "in your schedule");
    }
}
