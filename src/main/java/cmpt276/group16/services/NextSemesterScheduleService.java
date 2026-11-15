package cmpt276.group16.services;

import java.time.LocalDate;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import cmpt276.group16.models.*;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

//The term after the current one, worked out from today's date
//Empty if the term's course data hasn't been loaded yet
@Service
public class NextSemesterScheduleService {

    private final NextSemesterScheduleRepository scheduleRepository;
    private final ScheduledCourseRepository scheduledCourseRepository;
    private final SectionRepository sectionRepository;
    private final TermRepository termRepository;
    private final Clock clock;

    public NextSemesterScheduleService(NextSemesterScheduleRepository scheduleRepository,
            ScheduledCourseRepository scheduledCourseRepository,
            SectionRepository sectionRepository, TermRepository termRepository, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.scheduledCourseRepository = scheduledCourseRepository;
        this.sectionRepository = sectionRepository;
        this.termRepository = termRepository;
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
        Optional<NextSemesterSchedule> schedule = scheduleRepository.findByStudentAndTerm(student, term.get());
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
        } 
        else {
            primary = sectionRepository.findById(primarySectionId);
        }
        if (primary.isEmpty()) {
            return List.of("Choose a lecture section.");
        }

        // The lecture must belong to the chosen course, in next semester.
        // Checked here because the form can be edited
        CourseOffering offering = primary.get().getOffering();
        if (!offering.getId().equals(offeringId)
                || !offering.getTerm().getId().equals(term.get().getId())) {
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
        NextSemesterSchedule schedule = scheduleRepository.findByStudentAndTerm(student, term.get())
                .orElseGet(() -> scheduleRepository.save(new NextSemesterSchedule(student, term.get())));

        // Stop if the course is already in the schedule
        if (scheduledCourseRepository.existsByScheduleAndOffering(schedule, offering)) {
            return List.of("That course is already in your schedule.");
        }

        // All checks passed, so save it
        scheduledCourseRepository.save(new ScheduledCourse(schedule, offering, primary.get(), attached));
        return List.of();
    }

}
