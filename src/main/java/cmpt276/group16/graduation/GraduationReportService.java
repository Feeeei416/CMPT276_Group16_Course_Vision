package cmpt276.group16.graduation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.graduation.StudentCourse.State;
import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseDesignation;
import cmpt276.group16.models.CourseDesignationRepository;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.Designation;
import cmpt276.group16.models.Grades;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.ScheduledCourse;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.services.NextSemesterScheduleService;
import cmpt276.group16.services.PlanService;
import cmpt276.group16.services.PlanService.TermPlan;
import cmpt276.group16.transcript.Terms;

// Builds a student's Graduation Report (#69): loads their requirements, course record, main plan and
// next semester's schedule, and hands them to RequirementCheck.
// Nothing is saved, so the report always reflects the record and plan as they are when it's opened
@Service
public class GraduationReportService {

    private final GraduationRequirements graduationRequirements;
    private final TranscriptCourseRepository transcriptCourseRepository;
    private final PlanService planService;
    private final NextSemesterScheduleService scheduleService;
    private final CourseRepository courseRepository;
    private final CourseDesignationRepository designationRepository;

    public GraduationReportService(GraduationRequirements graduationRequirements,
                                   TranscriptCourseRepository transcriptCourseRepository, PlanService planService,
                                   NextSemesterScheduleService scheduleService, CourseRepository courseRepository,
                                   CourseDesignationRepository designationRepository) {
        this.graduationRequirements = graduationRequirements;
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.planService = planService;
        this.scheduleService = scheduleService;
        this.courseRepository = courseRepository;
        this.designationRepository = designationRepository;
    }

    // Empty when the student's program and entry year have no requirements yet, so the page can say so
    @Transactional(readOnly = true)
    public Optional<GraduationReport> forStudent(StudentProfile student) {
        return graduationRequirements.forStudent(student)
                .map(requirements -> RequirementCheck.report(requirements, coursesOf(student)));
    }

    // Every course that can count, once each:
    //  - from the record: the latest attempt that earned credit, or else the attempt in progress.
    //    A course only failed or withdrawn from doesn't count, unless it's planned again.
    //  - from the main plan and next semester's schedule: courses not already completed or in progress
    List<StudentCourse> coursesOf(StudentProfile student) {
        // The SFU course behind each code, for its WQB designations
        Map<String, Course> sfuCourses = new HashMap<>();
        List<Draft> drafts = new ArrayList<>();
        Set<String> taken = new HashSet<>();

        for (List<TranscriptCourse> attempts : attemptsByCode(transcriptCourseRepository.findByStudentOrderByIdAsc(student))) {
            Optional<TranscriptCourse> counted = latest(attempts.stream()
                    .filter(course -> Grades.earnsCredit(course.getGrade())).toList());
            State state = State.COMPLETED;
            if (counted.isEmpty()) {
                counted = latest(attempts.stream().filter(TranscriptCourse::isInProgress).toList());
                state = State.IN_PROGRESS;
            }
            if (counted.isEmpty()) {
                continue;
            }
            TranscriptCourse course = counted.get();
            Draft draft = new Draft(normalize(course.getSubject()), normalize(course.getNumber()), course.getUnits(),
                    state, state == State.COMPLETED ? course.getGrade() : null);
            drafts.add(draft);
            taken.add(draft.code());
            if (course.getCourse() != null) {
                sfuCourses.put(draft.code(), course.getCourse());
            }
        }

        // A course planned twice, or both planned and scheduled, counts once
        List<Course> plannedCourses = new ArrayList<>();
        for (TermPlan termPlan : planService.termPlans(student)) {
            termPlan.courses().stream().map(PlannedCourse::getCourse).forEach(plannedCourses::add);
        }
        scheduleService.coursesFor(student).stream()
                .map(ScheduledCourse::getOffering)
                .map(offering -> offering.getCourse())
                .forEach(plannedCourses::add);
        for (Course course : plannedCourses) {
            String code = course.getSubject() + " " + course.getNumber();
            if (taken.add(code)) {
                BigDecimal units = course.getUnits() == null ? BigDecimal.ZERO : course.getUnits();
                drafts.add(new Draft(course.getSubject(), course.getNumber(), units, State.PLANNED, null));
                sfuCourses.put(code, course);
            }
        }

        Map<String, Set<Designation>> designations = designations(drafts, sfuCourses);
        // A course the app has SFU's data for has all its designations listed. Any other course might have more
        return drafts.stream()
                .map(draft -> new StudentCourse(draft.subject(), draft.number(), draft.units(), draft.state(),
                        draft.grade(), designations.getOrDefault(draft.code(), Set.of()),
                        sfuCourses.containsKey(draft.code())))
                .toList();
    }

    // A course before its WQB designations are looked up
    private record Draft(String subject, String number, BigDecimal units, State state, String grade) {
        String code() {
            return subject + " " + number;
        }
    }

    // WQB designations come from SFU's course data. Record courses that weren't linked to it are looked up by code,
    // all in one query. A course SFU's data doesn't have still counts as W when its number ends in W, like CMPT 105W
    private Map<String, Set<Designation>> designations(List<Draft> drafts, Map<String, Course> sfuCourses) {
        List<Draft> unlinked = drafts.stream()
                .filter(draft -> !sfuCourses.containsKey(draft.code()) && !draft.number().contains("X"))
                .toList();
        if (!unlinked.isEmpty()) {
            Set<String> wanted = new HashSet<>();
            Set<String> subjects = new HashSet<>();
            Set<String> numbers = new HashSet<>();
            for (Draft draft : unlinked) {
                wanted.add(draft.code());
                subjects.add(draft.subject());
                numbers.add(draft.number());
            }
            for (Course course : courseRepository.findBySubjectInAndNumberIn(subjects, numbers)) {
                String code = course.getSubject() + " " + course.getNumber();
                if (wanted.contains(code)) {
                    sfuCourses.put(code, course);
                }
            }
        }

        Map<Long, String> codeById = new HashMap<>();
        sfuCourses.forEach((code, course) -> codeById.put(course.getId(), code));
        Map<String, Set<Designation>> byCode = new HashMap<>();
        if (!codeById.isEmpty()) {
            for (CourseDesignation row : designationRepository.findByCourseIdIn(codeById.keySet())) {
                byCode.computeIfAbsent(codeById.get(row.getId().getCourseId()),
                        code -> EnumSet.noneOf(Designation.class)).add(row.getDesignation());
            }
        }
        for (Draft draft : drafts) {
            if (draft.number().endsWith("W")) {
                byCode.computeIfAbsent(draft.code(), code -> EnumSet.noneOf(Designation.class)).add(Designation.W);
            }
        }
        return byCode;
    }

    // Attempts grouped by course code. Transfer credit like "MATH 1XX" isn't one SFU course,
    // so two of them are kept apart, the same way UnitTotals counts them
    private static List<List<TranscriptCourse>> attemptsByCode(List<TranscriptCourse> record) {
        List<List<TranscriptCourse>> groups = new ArrayList<>();
        Map<String, List<TranscriptCourse>> byCode = new LinkedHashMap<>();
        for (TranscriptCourse course : record) {
            if (normalize(course.getNumber()).contains("XX")) {
                groups.add(List.of(course));
            } else {
                String code = normalize(course.getSubject()) + " " + normalize(course.getNumber());
                byCode.computeIfAbsent(code, key -> new ArrayList<>()).add(course);
            }
        }
        groups.addAll(byCode.values());
        return groups;
    }

    // The most recent attempt, by term
    private static Optional<TranscriptCourse> latest(List<TranscriptCourse> attempts) {
        return attempts.stream().max((a, b) -> Terms.OLDEST_FIRST.compare(a.getTerm(), b.getTerm()));
    }

    // Courses added by hand can be in any case, like "cmpt 105w"
    private static String normalize(String text) {
        return text.trim().toUpperCase(Locale.ROOT);
    }
}
