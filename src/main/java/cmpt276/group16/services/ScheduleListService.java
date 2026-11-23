package cmpt276.group16.services;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.NextSemesterSchedule;
import cmpt276.group16.models.NextSemesterScheduleRepository;
import cmpt276.group16.models.ScheduledCourse;
import cmpt276.group16.models.ScheduledCourseRepository;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;

// A student's schedules for next semester as a whole, for the All my schedules page: making, copying, renaming and
// deleting them, and choosing which one is the main schedule. NextSemesterScheduleService changes what's inside
// a schedule.
// Only next semester's schedules are listed or found, since the schedule page only works with next semester's
// courses. Each change locks the student first, so a double click can't make two schedules with the same name
// or leave two main schedules
@Service
public class ScheduleListService {

    private final NextSemesterScheduleRepository scheduleRepository;
    private final ScheduledCourseRepository scheduledCourseRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final NextSemesterScheduleService scheduleService;

    public ScheduleListService(NextSemesterScheduleRepository scheduleRepository,
            ScheduledCourseRepository scheduledCourseRepository, StudentProfileRepository studentProfileRepository,
            NextSemesterScheduleService scheduleService) {
        this.scheduleRepository = scheduleRepository;
        this.scheduledCourseRepository = scheduledCourseRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.scheduleService = scheduleService;
    }

    // One row on the All my schedules page
    public record ScheduleSummary(NextSemesterSchedule schedule, long courseCount) {

        // For example "3 courses", or "1 course"
        public String sizeText() {
            return courseCount + (courseCount == 1 ? " course" : " courses");
        }
    }

    // The main schedule first, then the rest in the order they were made.
    // Empty until next semester's courses are loaded
    @Transactional(readOnly = true)
    public List<ScheduleSummary> schedules(StudentProfile student) {
        Optional<Term> term = scheduleService.nextSemesterTerm();
        if (term.isEmpty()) {
            return List.of();
        }
        return scheduleRepository.findByStudentAndTermOrderByIdAsc(student, term.get()).stream()
                .sorted(Comparator.comparing((NextSemesterSchedule schedule) -> !schedule.isMain()))
                .map(schedule -> new ScheduleSummary(schedule, scheduledCourseRepository.countBySchedule(schedule)))
                .toList();
    }

    // Empty when the schedule is another student's, or for a term that isn't next semester
    @Transactional(readOnly = true)
    public Optional<NextSemesterSchedule> find(StudentProfile student, Long scheduleId) {
        return scheduleService.nextSemesterTerm()
                .flatMap(term -> scheduleRepository.findByIdAndStudentAndTerm(scheduleId, student, term));
    }

    // copyFromId is null for an empty schedule. A student's first schedule becomes their main schedule,
    // since there has to be one
    @Transactional
    public PlanChange create(StudentProfile student, String nameText, Long copyFromId) {
        lock(student);
        Optional<Term> term = scheduleService.nextSemesterTerm();
        if (term.isEmpty()) {
            return PlanChange.rejected("Next semester's courses aren't available yet.");
        }
        String name = nameText.trim();
        List<String> problems = problemsWithNewName(student, term.get(), name);
        if (!problems.isEmpty()) {
            return PlanChange.rejected(problems);
        }
        if (copyFromId == null) {
            boolean first = scheduleRepository.findByStudentAndTermOrderByIdAsc(student, term.get()).isEmpty();
            scheduleRepository.save(new NextSemesterSchedule(student, term.get(), name, first));
            return PlanChange.saved("Made " + name + ".");
        }
        Optional<NextSemesterSchedule> source = scheduleRepository.findByIdAndStudentAndTerm(copyFromId, student,
                term.get());
        if (source.isEmpty()) {
            return PlanChange.rejected("The schedule to copy isn't one of your schedules.");
        }
        NextSemesterSchedule copy = scheduleRepository.save(new NextSemesterSchedule(student, term.get(), name, false));
        // The same lecture and lab or tutorial for each course, so the copy starts with the same timetable
        for (ScheduledCourse course : scheduledCourseRepository.findBySchedule(source.get())) {
            scheduledCourseRepository.save(new ScheduledCourse(copy, course.getOffering(), course.getPrimarySection(),
                    course.getAttachedSection()));
        }
        return PlanChange.saved("Made " + name + " as a copy of " + source.get().getName() + ".");
    }

    @Transactional
    public PlanChange rename(StudentProfile student, Long scheduleId, String nameText) {
        lock(student);
        Optional<NextSemesterSchedule> schedule = find(student, scheduleId);
        if (schedule.isEmpty()) {
            return PlanChange.rejected("That schedule isn't one of yours.");
        }
        String oldName = schedule.get().getName();
        String name = nameText.trim();
        if (name.equals(oldName)) {
            return PlanChange.rejected(name + " already has that name.");
        }
        // Changing only the capitals, like "morning labs" to "Morning labs", is allowed
        List<String> problems = name.equalsIgnoreCase(oldName)
                ? PlanRules.problemsWithScheduleName(name)
                : problemsWithNewName(student, schedule.get().getTerm(), name);
        if (!problems.isEmpty()) {
            return PlanChange.rejected(problems);
        }
        schedule.get().setName(name);
        return PlanChange.saved("Renamed " + oldName + " to " + name + ".");
    }

    // The main schedule can't be deleted, so My schedule always has one to show.
    // Its courses go first, since each one points at the schedule
    @Transactional
    public PlanChange delete(StudentProfile student, Long scheduleId) {
        lock(student);
        Optional<NextSemesterSchedule> schedule = find(student, scheduleId);
        if (schedule.isEmpty()) {
            return PlanChange.rejected("That schedule isn't one of yours.");
        }
        String name = schedule.get().getName();
        if (schedule.get().isMain()) {
            return PlanChange.rejected(name + " is your main schedule. Make another schedule your main schedule "
                    + "before deleting it.");
        }
        scheduledCourseRepository.deleteBySchedule(schedule.get());
        scheduleRepository.delete(schedule.get());
        return PlanChange.saved("Deleted " + name + ".");
    }

    @Transactional
    public PlanChange makeMain(StudentProfile student, Long scheduleId) {
        lock(student);
        Optional<NextSemesterSchedule> schedule = find(student, scheduleId);
        if (schedule.isEmpty()) {
            return PlanChange.rejected("That schedule isn't one of yours.");
        }
        String name = schedule.get().getName();
        if (schedule.get().isMain()) {
            return PlanChange.rejected(name + " is already your main schedule.");
        }
        scheduleRepository.findByStudentAndTermAndMainTrue(student, schedule.get().getTerm())
                .ifPresent(oldMain -> oldMain.setMain(false));
        schedule.get().setMain(true);
        return PlanChange.saved(name + " is now your main schedule.");
    }

    // Names only have to be different within one term, so last term's "My schedule" doesn't block this one's
    private List<String> problemsWithNewName(StudentProfile student, Term term, String name) {
        List<String> problems = PlanRules.problemsWithScheduleName(name);
        if (problems.isEmpty() && scheduleRepository.existsByStudentAndTermAndNameIgnoreCase(student, term, name)) {
            return List.of("You already have a schedule called " + name + ".");
        }
        return problems;
    }

    // Held until the change's transaction ends; another change for the same student waits here
    private void lock(StudentProfile student) {
        studentProfileRepository.findLockedByUserId(student.getUserId());
    }
}
