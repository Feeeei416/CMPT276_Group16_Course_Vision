package cmpt276.group16.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.CourseOfferingRepository;
import cmpt276.group16.models.Grades;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.RequisiteRuleRepository;
import cmpt276.group16.models.RuleKind;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.transcript.Terms;

// Sorts the courses offered next semester into the ones a student can take (#38),
// the ones they can't and why (#39), and the ones the app couldn't check
@Service
public class EligibilityService {

    // One course on the Eligible courses page.
    // notes: for an eligible course, things to confirm ("CMPT 225 is still in progress...");
    //        for a course they can't take, what's missing ("Needs CMPT 225 with C- or better, you have D.");
    //        for a course that couldn't be checked, the prerequisite text from the Calendar
    public record CourseResult(Course course, List<String> notes) {
    }

    // term: the term being checked, for example "2027 Spring".
    // termKnown: false when there is no course data for that term yet, so every list is empty
    public record Eligibility(String term, boolean termKnown, List<CourseResult> eligible,
            List<CourseResult> notEligible, List<CourseResult> unchecked) {
    }

    private final TermRepository termRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final RequisiteRuleRepository requisiteRuleRepository;
    private final TranscriptCourseRepository transcriptCourseRepository;
    private final Clock clock;

    public EligibilityService(TermRepository termRepository, CourseOfferingRepository courseOfferingRepository,
            RequisiteRuleRepository requisiteRuleRepository, TranscriptCourseRepository transcriptCourseRepository,
            Clock clock) {
        this.termRepository = termRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.requisiteRuleRepository = requisiteRuleRepository;
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.clock = clock;
    }

    // Next semester from today's date in Vancouver, for example "2027 Spring" in November 2026
    public Eligibility forNextSemester(StudentProfile student) {
        return forTerm(student, PlanRules.nextSemester(LocalDate.now(clock)));
    }

    public Eligibility forTerm(StudentProfile student, String termName) {
        Optional<Term> term = findTerm(termName);
        if (term.isEmpty()) {
            return new Eligibility(termName, false, List.of(), List.of(), List.of());
        }

        List<TranscriptCourse> record = transcriptCourseRepository.findByStudentOrderByIdAsc(student);
        // Only courses from earlier terms can meet a prerequisite for this term
        List<TranscriptCourse> taken = PrerequisiteCheck.takenBefore(record, termName);
        Set<String> alreadyTakingOrPassed = passedOrInProgress(record);

        List<CourseResult> eligible = new ArrayList<>();
        List<CourseResult> notEligible = new ArrayList<>();
        List<CourseResult> unchecked = new ArrayList<>();

        for (CourseOffering offering : courseOfferingRepository
                .findByTermIdOrderByCourseSubjectAscCourseNumberAsc(term.get().getId())) {
            Course course = offering.getCourse();
            if (!offering.isOffered() || alreadyTakingOrPassed.contains(code(course.getSubject(), course.getNumber()))) {
                continue;
            }
            if (!canBeChecked(course)) {
                unchecked.add(new CourseResult(course, List.of(course.getPrereqText().trim())));
                continue;
            }
            List<RequisiteRule> rules = requisiteRuleRepository
                    .findByCourseIdAndRuleKindOrderByIdAsc(course.getId(), RuleKind.PREREQUISITE);
            PrerequisiteCheck.Result result = PrerequisiteCheck.check(rules, taken);
            if (result.met()) {
                eligible.add(new CourseResult(course, result.toConfirm()));
            } else {
                notEligible.add(new CourseResult(course, result.missing()));
            }
        }
        return new Eligibility(termName, true, eligible, notEligible, unchecked);
    }

    // PARSED means the rules are saved (none saved means no prerequisites).
    // A course with no prerequisite text has nothing to check either.
    // Otherwise the parser failed or hasn't run yet, so the student is shown the text instead
    private static boolean canBeChecked(Course course) {
        return course.getParseStatus() == Course.ParseStatus.PARSED
                || course.getPrereqText() == null
                || course.getPrereqText().isBlank();
    }

    // "2027 Spring" -> the saved term, if any course data exists for it
    private Optional<Term> findTerm(String termName) {
        String year = Terms.yearOf(termName);
        Optional<Season> season = PlanRules.parseSeason(Terms.seasonOf(termName));
        if (year.isEmpty() || season.isEmpty()) {
            return Optional.empty();
        }
        return termRepository.findByTermYearAndSeason(Integer.parseInt(year), season.get());
    }

    // Courses already passed, or being taken now, aren't suggested again
    private static Set<String> passedOrInProgress(List<TranscriptCourse> record) {
        return record.stream()
                .filter(c -> c.isInProgress() || Grades.earnsCredit(c.getGrade()))
                .map(c -> code(c.getSubject(), c.getNumber()))
                .collect(Collectors.toSet());
    }

    private static String code(String subject, String number) {
        return (subject + " " + number).toUpperCase(Locale.ROOT);
    }
}