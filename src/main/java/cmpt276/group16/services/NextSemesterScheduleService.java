package cmpt276.group16.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.Optional;
import org.springframework.stereotype.Service;
import cmpt276.group16.models.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

//The term after the current one, worked out from today's date
//Empty if the term's course data hasn't been loaded yet
@Service
public class NextSemesterScheduleService {
    
    public static final String MAIN_PLAN_NAME = "My plan";

    public record ClassTime(String course, String section, SectionType type,
            LocalTime start, LocalTime end) {
    }

    private final NextSemesterScheduleRepository scheduleRepository;
    private final ScheduledCourseRepository scheduledCourseRepository;
    private final SectionRepository sectionRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final TermRepository termRepository;
    private final Clock clock;

    public NextSemesterScheduleService(NextSemesterScheduleRepository scheduleRepository,
            ScheduledCourseRepository scheduledCourseRepository,
            SectionRepository sectionRepository, TermRepository termRepository,
            CourseOfferingRepository courseOfferingRepository, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.scheduledCourseRepository = scheduledCourseRepository;
        this.sectionRepository = sectionRepository;
        this.termRepository = termRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.clock = clock;
    }

    public Optional<Term> nextSemesterTerm() {

        LocalDate today = LocalDate.now(clock);
        int month = today.getMonthValue();
        int year = today.getYear();

        if (month <= 4) {
            return termRepository.findByTermYearAndSeason(year, Season.SUMMER);
        }
        if (month <= 8) {
            return termRepository.findByTermYearAndSeason(year, Season.FALL);
        }
        return termRepository.findByTermYearAndSeason(year + 1, Season.SPRING);

    }

    // Returns the courses in the student's next semester schedule, or empty if the
    // term isn't known yet
    @Transactional(readOnly = true)
    public List<ScheduledCourse> coursesFor(StudentProfile student) {
        Optional<Term> term = nextSemesterTerm();
        if (term.isEmpty()) {
            return List.of();
        }
        Optional<NextSemesterSchedule> schedule = scheduleRepository.findByStudentAndTermAndMainTrue(student, term.get());
        if (schedule.isEmpty()) {
            return List.of();
        }
        return scheduledCourseRepository.findBySchedule(schedule.get());
    }

    // Adds a course to the student's next semester schedule: lecture and its lab or
    // tutorial if it has one
    // Returns every problem found
    @Transactional
    public List<String> addCourse(StudentProfile student, Long offeringId,
            Long primarySectionId, Long attachedSectionId) {

        // Which semester is next? Stop if its data isn't loaded yet
        Optional<Term> term = nextSemesterTerm();
        if (term.isEmpty()) {
            return List.of("Next semester's courses aren't available yet.");
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
                || !offering.isOffered()) {
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
                .anyMatch(s -> !s.isPrimarySection()
                        && Objects.equals(s.getAssociatedClass(), primary.get().getAssociatedClass()));
        if (lectureHasAttached && attachedSectionId == null) {
            return List.of("Choose a lab or tutorial for this lecture.");
        }

        // If they picked a lab or tutorial: it must exist, be in this course,
        // and go with the chosen lecture (same associatedClass)
        Section attached = null;
        if (attachedSectionId != null) {
            Optional<Section> found = sectionRepository.findById(attachedSectionId);
            if (found.isEmpty() || !found.get().getOffering().getId().equals(offering.getId())) {
                return List.of("That lab or tutorial isn't part of this course.");
            }
            if (found.get().isPrimarySection()
                    || !Objects.equals(found.get().getAssociatedClass(), primary.get().getAssociatedClass())) {
                return List.of("That lab or tutorial doesn't go with the lecture you chose.");
            }
            attached = found.get();
        }

        // Find the student's schedule, or create it if this is their first course
        NextSemesterSchedule schedule = scheduleRepository.findByStudentAndTermAndMainTrue(student, term.get())
                .orElseGet(() -> scheduleRepository.save(new NextSemesterSchedule(student, term.get())));

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
        String subject = parsed.get().subject();
        String number = parsed.get().number();
        return offeredNextSemester().stream()
                .filter(o -> o.getCourse().getSubject().equals(subject)
                        && o.getCourse().getNumber().equals(number))
                .findFirst();
    }

    // True for text like "CMPT 225" or "cmpt225", false for "225" or "data
    // structures"
    public boolean isCourseCode(String code) {
        return PlanRules.parseCourseCode(code).isPresent();
    }

    // A course's sections, sorted by code, with their times loaded, for the lecture
    // and tutorial dropdowns
    @Transactional(readOnly = true)
    public List<Section> sectionsOf(CourseOffering offering) {
        return sectionRepository.findByOfferingIdOrderBySectionCodeAsc(offering.getId());
    }

    // The student's class meetings next semester grouped by weekday, Monday to
    // Friday first,
    // each day sorted by start time. Sections without published times aren't
    // included
    @Transactional(readOnly = true)
    public Map<DayOfWeek, List<ClassTime>> meetingsByDay(StudentProfile student) {
        Map<DayOfWeek, List<ClassTime>> days = new LinkedHashMap<>();
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            days.put(day, new ArrayList<>());
        }
        for (ScheduledCourse course : coursesFor(student)) {
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
}
