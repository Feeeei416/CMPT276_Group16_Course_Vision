package cmpt276.group16.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import cmpt276.group16.models.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
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

    // Returns the courses in the student's next semester schedule, or empty if the
    // term isn't known yet
    @Transactional(readOnly = true)
    public List<ScheduledCourse> coursesFor(StudentProfile student) {
        Optional<Term> term = nextSemesterTerm();
        if (term.isEmpty()) {
            return List.of();
        }
        Optional<NextSemesterSchedule> schedule = scheduleRepository.findByStudentAndTermAndMainTrue(student,
                term.get());
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

        // Locks the student so a double click waits for the first request instead of
        // failing
        studentProfileRepository.findLockedByUserId(student.getUser().getId());

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

        // Find the student's schedule, or create it if this is their first course
        NextSemesterSchedule schedule = scheduleRepository.findByStudentAndTermAndMainTrue(student, term.get())
                .orElseGet(() -> scheduleRepository
                        .save(new NextSemesterSchedule(student, term.get(), MAIN_SCHEDULE_NAME, true)));

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
    // and tutorial dropdowns. Sections SFU has cancelled are kept by the import but
    // left out here
    @Transactional(readOnly = true)
    public List<Section> sectionsOf(CourseOffering offering) {
        return sectionRepository.findByOfferingIdOrderBySectionCodeAsc(offering.getId()).stream()
                .filter(Section::isOffered)
                .toList();
    }

    // The student's class meetings next semester grouped by weekday, Monday to
    // Friday first,
    // each day sorted by start time. Sections without published times aren't
    // included
    @Transactional(readOnly = true)
    public Map<DayOfWeek, List<ClassTime>> meetingsByDay(StudentProfile student) {
        Map<DayOfWeek, List<ClassTime>> days = new EnumMap<>(DayOfWeek.class);
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

    // The total units of every course in the student's schedule next semester
    @Transactional(readOnly = true)
    public BigDecimal totalUnits(StudentProfile student) {
        BigDecimal total = BigDecimal.ZERO;
        for (ScheduledCourse course : coursesFor(student)) {
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

    // Every pair of the student's classes that overlap on the same day, for the
    // warnings
    @Transactional(readOnly = true)
    public List<Conflict> conflicts(StudentProfile student) {
        List<Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<DayOfWeek, List<ClassTime>> day : meetingsByDay(student).entrySet()) {
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

    // Information to describe one class on the timetable grid
    public record TimetableBlock(
            int column,
            int rowStart,
            int rowEnd,
            String label,
            boolean conflict,
            int color,
            int lane,
            int lanes) {
    }

    private static final int MINUTES_PER_ROW = 10;
    private static final int COLORS = 8;

    // Row 1 is the day names, so 8:00 is row 2, 8:10 is row 3, and so on
    static int rowFor(LocalTime time, int startHour) {
        int minutes = (time.getHour() - startHour) * 60 + time.getMinute();
        return minutes / MINUTES_PER_ROW + 2;
    }

    // The hours the grid covers: 8:00 to 18:00, stretched to fit earlier or later
    // classes
    public record GridHours(int start, int end) {
    }

    @Transactional(readOnly = true)
    public GridHours gridHours(StudentProfile student) {
        int start = 8;
        int end = 18;
        for (List<ClassTime> day : meetingsByDay(student).values()) {
            for (ClassTime t : day) {
                start = Math.min(start, t.start().getHour());
                int endHour = t.end().getMinute() > 0 ? t.end().getHour() + 1 : t.end().getHour();
                end = Math.max(end, endHour);
            }
        }
        return new GridHours(start, end);
    }

    // Where a block sits across its day column: lane 0 is the left, lanes is how
    // many share the width
    record LanePlace(int lane, int lanes) {
    }

    // Seats one day's classes (sorted by start time) side by side where they
    // overlap
    static List<LanePlace> lanesFor(List<ClassTime> classes) {
        int[] lane = new int[classes.size()];
        int[] lanes = new int[classes.size()];
        List<LocalTime> laneEnds = new ArrayList<>();
        int groupStart = 0;
        LocalTime groupEnd = null;

        for (int i = 0; i < classes.size(); i++) {
            ClassTime t = classes.get(i);

            // This class starts after the whole group has ended, so close that group
            if (groupEnd != null && !t.start().isBefore(groupEnd)) {
                for (int k = groupStart; k < i; k++) {
                    lanes[k] = laneEnds.size();
                }
                laneEnds.clear();
                groupStart = i;
                groupEnd = null;
            }

            // Take the first lane that's free by the time this class starts
            int chosen = -1;
            for (int l = 0; l < laneEnds.size(); l++) {
                if (!t.start().isBefore(laneEnds.get(l))) {
                    chosen = l;
                    break;
                }
            }
            // No lane is free, so open a new one
            if (chosen == -1) {
                laneEnds.add(t.end());
                chosen = laneEnds.size() - 1;
            } else {
                laneEnds.set(chosen, t.end());
            }
            lane[i] = chosen;

            if (groupEnd == null || t.end().isAfter(groupEnd)) {
                groupEnd = t.end();
            }
        }
        // Close the last group
        for (int k = groupStart; k < classes.size(); k++) {
            lanes[k] = laneEnds.size();
        }

        List<LanePlace> places = new ArrayList<>();
        for (int i = 0; i < classes.size(); i++) {
            places.add(new LanePlace(lane[i], lanes[i]));
        }
        return places;
    }

    // Every class in the student's week, turned into a box with a grid position
    @Transactional(readOnly = true)
    public List<TimetableBlock> timetableBlocks(StudentProfile student) {
        int startHour = gridHours(student).start();
        List<String> courseOrder = new ArrayList<>();
        for (ScheduledCourse course : coursesFor(student)) {
            courseOrder.add(course.getOffering().getCourse().getSubject() + " "
                    + course.getOffering().getCourse().getNumber());
        }
        Set<ClassTime> clashing = new HashSet<>();
        for (Conflict c : conflicts(student)) {
            clashing.add(c.first());
            clashing.add(c.second());
        }

        List<TimetableBlock> blocks = new ArrayList<>();
        for (Map.Entry<DayOfWeek, List<ClassTime>> day : meetingsByDay(student).entrySet()) {
            int column = day.getKey().getValue() + 1; // column 1 is the times, Monday is 2
            List<ClassTime> classes = day.getValue();
            List<LanePlace> places = lanesFor(classes);
            for (int i = 0; i < classes.size(); i++) {
                ClassTime t = classes.get(i);
                LanePlace place = places.get(i);
                blocks.add(new TimetableBlock(
                        column,
                        rowFor(t.start(), startHour),
                        rowFor(t.end(), startHour),
                        t.course() + " " + t.section() + " " + t.type(),
                        clashing.contains(t),
                        courseOrder.indexOf(t.course()) % COLORS,
                        place.lane(),
                        place.lanes()));
            }
        }
        return blocks;
    }
}
