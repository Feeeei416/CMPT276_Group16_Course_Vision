package cmpt276.group16.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.Optional;
import org.springframework.stereotype.Service;
import cmpt276.group16.models.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

//The term after the current one, worked out from today's date
//Empty if the term's course data hasn't been loaded yet
@Service
public class NextSemesterScheduleService {

    public static final String MAIN_SCHEDULE_NAME = "My schedule";
    public static final BigDecimal MAX_UNITS = new BigDecimal("18");
    public static final BigDecimal MAX_UNITS_COMPUTER_ENGINEERING = new BigDecimal("22");

    public record ClassTime(String course, String section, SectionType type,
            LocalTime start, LocalTime end) {
    }

    // Two classes that meet at the same time on the same day
    public record Conflict(DayOfWeek day, ClassTime first, ClassTime second) {
    }

    private final NextSemesterScheduleRepository scheduleRepository;
    private final ScheduledCourseRepository scheduledCourseRepository;
    private final SectionRepository sectionRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final TermRepository termRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final Clock clock;

    public NextSemesterScheduleService(NextSemesterScheduleRepository scheduleRepository,
            ScheduledCourseRepository scheduledCourseRepository,
            SectionRepository sectionRepository, TermRepository termRepository,
            CourseOfferingRepository courseOfferingRepository, StudentProfileRepository studentProfileRepository,
            Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.scheduledCourseRepository = scheduledCourseRepository;
        this.sectionRepository = sectionRepository;
        this.termRepository = termRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.clock = clock;
    }

    public Optional<Term> nextSemesterTerm() {

        PlanRules.YearAndSeason next = PlanRules.nextSemesterYearAndSeason(LocalDate.now(clock));
        return termRepository.findByTermYearAndSeason(next.year(), next.season());
    }

    // The schedule My schedule opens to. Empty until the student adds their first course or makes their
    // first schedule, or when next semester's courses aren't loaded yet
    @Transactional(readOnly = true)
    public Optional<NextSemesterSchedule> mainSchedule(StudentProfile student) {
        return nextSemesterTerm()
                .flatMap(term -> scheduleRepository.findByStudentAndTermAndMainTrue(student, term));
    }

    // Returns the courses in the student's main schedule for next semester, or empty if they don't have one yet
    @Transactional(readOnly = true)
    public List<ScheduledCourse> coursesFor(StudentProfile student) {
        return mainSchedule(student).map(scheduledCourseRepository::findBySchedule).orElse(List.of());
    }

    // The courses in one schedule, for showing any of the student's schedules on the same page
    @Transactional(readOnly = true)
    public List<ScheduledCourse> coursesFor(NextSemesterSchedule schedule) {
        return scheduledCourseRepository.findBySchedule(schedule);
    }

    // Adds a course to the student's main schedule for next semester, making the schedule if it's their
    // first course. Calls the version below directly, so it needs its own @Transactional
    @Transactional
    public List<String> addCourse(StudentProfile student, Long offeringId,
            Long primarySectionId, Long attachedSectionId) {
        return addCourse(student, null, offeringId, primarySectionId, attachedSectionId);
    }

    // Adds a course to one of the student's schedules: lecture and its lab or tutorial if it has one.
    // scheduleId is the schedule to add it to, or null for the main schedule.
    // Returns every problem found
    @Transactional
    public List<String> addCourse(StudentProfile student, Long scheduleId, Long offeringId,
            Long primarySectionId, Long attachedSectionId) {

        // Locks the student so a double click waits for the first request instead of
        // failing
        studentProfileRepository.findLockedByUserId(student.getUser().getId());

        // Which semester is next? Stop if its data isn't loaded yet
        Optional<Term> term = nextSemesterTerm();
        if (term.isEmpty()) {
            return List.of("Next semester's courses aren't available yet.");
        }

        // Checked first, since the id comes from the form and can be changed to another student's schedule
        Optional<NextSemesterSchedule> chosenSchedule = Optional.empty();
        if (scheduleId != null) {
            chosenSchedule = scheduleRepository.findByIdAndStudentAndTerm(scheduleId, student, term.get());
            if (chosenSchedule.isEmpty()) {
                return List.of("That schedule isn't one of yours.");
            }
        }

        // Look up the lecture id the browser sent. Stop if it doesn't exist
        Optional<Section> primary;
        if (primarySectionId == null) {
            primary = Optional.empty();
        } else {
            primary = sectionRepository.findById(primarySectionId);
        }
        if (primary.isEmpty()) {
            return List.of("Choose a lecture section.");
        }

        // The lecture must belong to the chosen course, in next semester.
        // Checked here because the form can be edited
        CourseOffering offering = primary.get().getOffering();
        if (!offering.getId().equals(offeringId)
                || !offering.getTerm().getId().equals(term.get().getId())
                || !offering.isOffered() || !primary.get().isOffered()) {
            return List.of("That section isn't part of this course next semester.");
        }

        // It must be the section you enroll in (usually the lecture), not a lab or
        // tutorial
        if (!primary.get().isPrimarySection()) {
            return List.of("Choose a lecture section first, then its lab or tutorial.");
        }

        // If this lecture has labs or tutorials, the student must pick one
        boolean lectureHasAttached = sectionRepository
                .findByOfferingIdOrderBySectionCodeAsc(offering.getId()).stream()
                .anyMatch(s -> s.isOffered() && goesWith(s, primary.get()));
        if (lectureHasAttached && attachedSectionId == null) {
            return List.of("Choose a lab or tutorial for this lecture.");
        }

        // If they picked a lab or tutorial: it must exist, be in this course,
        // and go with the chosen lecture (same associatedClass)
        Section attached = null;
        if (attachedSectionId != null) {
            Optional<Section> found = sectionRepository.findById(attachedSectionId);
            if (found.isEmpty() || !found.get().getOffering().getId().equals(offering.getId())
                    || !found.get().isOffered()) {
                return List.of("That lab or tutorial isn't part of this course.");
            }
            if (!goesWith(found.get(), primary.get())) {
                return List.of("That lab or tutorial doesn't go with the lecture you chose.");
            }
            attached = found.get();
        }

        // The chosen schedule, or the student's main one, made now if this is their first course
        NextSemesterSchedule schedule = chosenSchedule.orElseGet(() -> scheduleRepository
                .findByStudentAndTermAndMainTrue(student, term.get())
                .orElseGet(() -> scheduleRepository
                        .save(new NextSemesterSchedule(student, term.get(), MAIN_SCHEDULE_NAME, true))));

        // Stop if the course is already in the schedule
        if (scheduledCourseRepository.existsByScheduleAndOffering(schedule, offering)) {
            return List.of("That course is already in your schedule.");
        }

        // All checks passed, so save it
        scheduledCourseRepository.save(new ScheduledCourse(schedule, offering, primary.get(), attached));
        return List.of();
    }

    // Removes one course from the student's schedule.
    // Only finds the course if it's in this student's schedule, so nobody can
    // remove someone else's
    @Transactional
    public List<String> removeCourse(StudentProfile student, Long scheduledCourseId) {
        // Locks the student so a double click waits for the first request instead of
        // failing
        studentProfileRepository.findLockedByUserId(student.getUser().getId());
        Optional<ScheduledCourse> course = scheduledCourseRepository.findByIdAndScheduleStudent(scheduledCourseId,
                student);
        if (course.isEmpty()) {
            return List.of("That course isn't in your schedule.");
        }
        scheduledCourseRepository.delete(course.get());
        return List.of();
    }

    // Every course that runs next semester, sorted by code, for the search box
    // suggestions
    @Transactional(readOnly = true)
    public List<CourseOffering> offeredNextSemester() {
        Optional<Term> term = nextSemesterTerm();
        if (term.isEmpty()) {
            return List.of();
        }
        return courseOfferingRepository
                .findByTermIdOrderByCourseSubjectAscCourseNumberAsc(term.get().getId())
                .stream()
                .filter(CourseOffering::isOffered)
                .toList();
    }

    // Turns typed text like "cmpt 225" into that course's offering next semester.
    // Empty if the text isn't a course code or the course doesn't run next semester
    @Transactional(readOnly = true)
    public Optional<CourseOffering> findOffering(String code) {
        Optional<PlanRules.CourseCode> parsed = PlanRules.parseCourseCode(code);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Optional<Term> term = nextSemesterTerm();
        if (term.isEmpty()) {
            return Optional.empty();
        }
        return courseOfferingRepository
                .findByTermIdAndCourseSubjectAndCourseNumber(term.get().getId(),
                        parsed.get().subject(), parsed.get().number())
                .filter(CourseOffering::isOffered);

    }

    // A course's sections, sorted by code, with their times loaded, for the lecture
    // and tutorial dropdowns. Sections SFU has cancelled are kept by the import but left out here
    @Transactional(readOnly = true)
    public List<Section> sectionsOf(CourseOffering offering) {
        return sectionRepository.findByOfferingIdOrderBySectionCodeAsc(offering.getId()).stream()
                .filter(Section::isOffered)
                .toList();
    }

    // The class meetings in the student's main schedule. The versions below that take a list of courses
    // work for any schedule
    @Transactional(readOnly = true)
    public Map<DayOfWeek, List<ClassTime>> meetingsByDay(StudentProfile student) {
        return meetingsByDay(coursesFor(student));
    }

    // The class meetings of these courses grouped by weekday, Monday to Friday first,
    // each day sorted by start time. Sections without published times aren't
    // included
    @Transactional(readOnly = true)
    public Map<DayOfWeek, List<ClassTime>> meetingsByDay(List<ScheduledCourse> courses) {
        Map<DayOfWeek, List<ClassTime>> days = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            days.put(day, new ArrayList<>());
        }
        for (ScheduledCourse course : courses) {
            String code = course.getOffering().getCourse().getSubject() + " "
                    + course.getOffering().getCourse().getNumber();
            List<Section> sections = new ArrayList<>();
            sections.add(course.getPrimarySection());
            if (course.getAttachedSection() != null) {
                sections.add(course.getAttachedSection());
            }
            for (Section section : sections) {
                for (SectionMeeting meeting : section.getMeetings()) {
                    days.computeIfAbsent(meeting.getDayOfWeek(), d -> new ArrayList<>())
                            .add(new ClassTime(code, section.getSectionCode(), section.getSectionType(),
                                    meeting.getStartTime(), meeting.getEndTime()));
                }
            }
        }
        days.values().forEach(list -> list.sort(Comparator.comparing(ClassTime::start)));
        return days;
    }

    // The total units of every course in the student's main schedule next semester
    @Transactional(readOnly = true)
    public BigDecimal totalUnits(StudentProfile student) {
        return totalUnits(coursesFor(student));
    }

    // The total units of these courses
    @Transactional(readOnly = true)
    public BigDecimal totalUnits(List<ScheduledCourse> courses) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScheduledCourse course : courses) {
            total = total.add(course.getOffering().getCourse().getUnits());
        }
        return total;
    }

    // The student's unit limit for a term: 22 for Computer Engineering (an
    // Engineering Science program), 18 otherwise
    public BigDecimal unitLimitFor(StudentProfile student) {
        if (student.getProgram() == Program.COMPUTER_ENGINEERING) {
            return MAX_UNITS_COMPUTER_ENGINEERING;
        }
        return MAX_UNITS;
    }

    // Every pair of classes in the student's main schedule that overlap on the same day
    @Transactional(readOnly = true)
    public List<Conflict> conflicts(StudentProfile student) {
        return conflicts(coursesFor(student));
    }

    // Every pair of these courses' classes that overlap on the same day, for the warnings
    @Transactional(readOnly = true)
    public List<Conflict> conflicts(List<ScheduledCourse> courses) {
        List<Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<DayOfWeek, List<ClassTime>> day : meetingsByDay(courses).entrySet()) {
            List<ClassTime> classes = day.getValue();
            for (int i = 0; i < classes.size(); i++) {
                for (int j = i + 1; j < classes.size(); j++) {
                    if (overlaps(classes.get(i), classes.get(j))) {
                        conflicts.add(new Conflict(day.getKey(), classes.get(i), classes.get(j)));
                    }
                }
            }
        }
        return conflicts;
    }

    private static boolean overlaps(ClassTime a, ClassTime b) {
        return a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }

    // Checks that the chosen lab or tutorial belongs to the chosen lecture
    // A lecture and its labs/tutorials share the same group number(associated
    // class)
    // If the lecture has no group number, any lab or tutorial of the course can be
    // selected
    private static boolean goesWith(Section attached, Section lecture) {
        if (attached.isPrimarySection()) {
            return false;
        }
        if (lecture.getAssociatedClass() == null) {
            return true;
        }
        return Objects.equals(attached.getAssociatedClass(), lecture.getAssociatedClass());
    }

}
