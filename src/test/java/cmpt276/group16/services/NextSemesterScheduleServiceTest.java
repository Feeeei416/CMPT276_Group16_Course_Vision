package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import cmpt276.group16.services.NextSemesterScheduleService.TimetableBlock;
import cmpt276.group16.services.NextSemesterScheduleService.GridHours;

import cmpt276.group16.models.*;

// @DataJpaTest only loads repositories, so the service is built by hand with a fixed date
@DataJpaTest
public class NextSemesterScheduleServiceTest {
    private static final Clock NOV_9_2026 = Clock.fixed(Instant.parse("2026-11-09T20:00:00Z"),
            ZoneId.of("America/Vancouver"));

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentProfileRepository studentProfileRepository;
    @Autowired
    private TermRepository termRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseOfferingRepository courseOfferingRepository;
    @Autowired
    private SectionRepository sectionRepository;
    @Autowired
    private NextSemesterScheduleRepository scheduleRepository;
    @Autowired
    private ScheduledCourseRepository scheduledCourseRepository;

    private NextSemesterScheduleService service;
    private StudentProfile priya;
    private StudentProfile marcus;
    private CourseOffering cmpt225;
    private Section d100, d101, d200, d201;

    @BeforeEach
    void setUp() {
        service = new NextSemesterScheduleService(scheduleRepository, scheduledCourseRepository,
                sectionRepository, termRepository, courseOfferingRepository, studentProfileRepository, NOV_9_2026);
        priya = saveStudent("priya@example.com");
        marcus = saveStudent("marcus@example.com");

        // CMPT 225 in 2027 Spring: two lectures, each with its own tutorial
        Term spring2027 = termRepository.save(new Term(2027, Season.SPRING));
        Course course = courseRepository.save(
                new Course("CMPT", "225", "Data Structures", new BigDecimal("3"), null, null, null));
        cmpt225 = courseOfferingRepository.save(new CourseOffering(course, spring2027, true));
        d100 = sectionRepository.save(new Section(cmpt225, "D100", SectionType.LEC, true, "1"));
        d101 = sectionRepository.save(new Section(cmpt225, "D101", SectionType.TUT, false, "1"));
        d200 = sectionRepository.save(new Section(cmpt225, "D200", SectionType.LEC, true, "2"));
        d201 = sectionRepository.save(new Section(cmpt225, "D201", SectionType.TUT, false, "2"));
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    // Saves a course offered next semester with one lecture at the given time, and
    // returns the lecture
    private Section saveLecture(String subject, String number, String units,
            DayOfWeek day, LocalTime start, LocalTime end) {
        Term spring2027 = termRepository.findByTermYearAndSeason(2027, Season.SPRING).orElseThrow();
        Course course = courseRepository.save(
                new Course(subject, number, subject + " " + number, new BigDecimal(units), null, null, null));
        CourseOffering offering = courseOfferingRepository.save(new CourseOffering(course, spring2027, true));
        Section lecture = new Section(offering, "D100", SectionType.LEC, true, "1");
        lecture.replaceMeetings(List.of(new SectionMeeting(day, start, end, null)));
        return sectionRepository.save(lecture);
    }

    // Test 1: the happy path
    @Test
    void addsALectureAndItsTutorial() {
        assertThat(service.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId())).isEmpty();
        assertThat(service.coursesFor(priya)).hasSize(1);
    }

    // Test 2: D201 belongs to lecture D200, so it can't be paired with D100
    @Test
    void rejectsATutorialFromAnotherLecture() {
        assertThat(service.addCourse(priya, cmpt225.getId(), d100.getId(), d201.getId()))
                .containsExactly("That lab or tutorial doesn't go with the lecture you chose.");
        assertThat(service.coursesFor(priya)).isEmpty();
    }

    // Test 3: a course can only be in the schedule once, even with different
    // sections
    @Test
    void rejectsTheSameCourseTwice() {
        service.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());

        assertThat(service.addCourse(priya, cmpt225.getId(), d200.getId(), d201.getId()))
                .containsExactly("That course is already in your schedule.");
        assertThat(service.coursesFor(priya)).hasSize(1);
    }

    // Test 4: Marcus uses Priya's course id; nothing of hers changes
    @Test
    void cantRemoveAnotherStudentsCourse() {
        service.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId());
        Long priyasCourseId = service.coursesFor(priya).get(0).getId();

        assertThat(service.removeCourse(marcus, priyasCourseId))
                .containsExactly("That course isn't in your schedule.");
        assertThat(service.coursesFor(priya)).hasSize(1);
    }

    // Test 5: in November 2026, next semester is 2027 Spring
    @Test
    void nextSemesterInNovemberIsSpringOfNextYear() {
        Term term = service.nextSemesterTerm().orElseThrow();

        assertThat(term.getTermYear()).isEqualTo(2027);
        assertThat(term.getSeason()).isEqualTo(Season.SPRING);
    }

    // A tutorial SFU has cancelled can't be picked, even by editing the form
    @Test
    void rejectsACancelledTutorial() {
        d101.setOffered(false);
        sectionRepository.save(d101);

        assertThat(service.addCourse(priya, cmpt225.getId(), d100.getId(), d101.getId()))
                .containsExactly("That lab or tutorial isn't part of this course.");
        assertThat(service.coursesFor(priya)).isEmpty();
    }

    // A lecture whose only tutorial was cancelled can be added on its own
    @Test
    void lectureWhoseTutorialWasCancelledNeedsNoTutorial() {
        d101.setOffered(false);
        sectionRepository.save(d101);

        assertThat(service.addCourse(priya, cmpt225.getId(), d100.getId(), null)).isEmpty();
    }

    // A cancelled section isn't offered in the dropdowns
    @Test
    void sectionsOfLeavesOutCancelledSections() {
        d201.setOffered(false);
        sectionRepository.save(d201);

        assertThat(service.sectionsOf(cmpt225)).extracting(Section::getSectionCode)
                .containsExactly("D100", "D101", "D200");
    }

    // Test 6: D100 has a tutorial, so it can't be added without one
    @Test
    void requiresATutorialWhenTheLectureHasOne() {
        assertThat(service.addCourse(priya, cmpt225.getId(), d100.getId(), null))
                .containsExactly("Choose a lab or tutorial for this lecture.");
        assertThat(service.coursesFor(priya)).isEmpty();
    }

    // #41: total units adds up every course in the schedule
    @Test
    void totalUnitsAddsUpCourses() {
        Section macm = saveLecture("MACM", "201", "3", DayOfWeek.TUESDAY, LocalTime.of(9, 30), LocalTime.of(10, 20));
        Section math = saveLecture("MATH", "152", "4", DayOfWeek.WEDNESDAY, LocalTime.of(9, 30), LocalTime.of(10, 20));
        service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);
        service.addCourse(priya, math.getOffering().getId(), math.getId(), null);

        assertThat(service.totalUnits(priya)).isEqualByComparingTo("7");
    }

    // #41: Computer Engineering gets SFU's Engineering Science limit
    @Test
    void unitLimitDependsOnProgram() {
        User user = userRepository.saveAndFlush(new User("ana@example.com", "hash", "Student", Role.STUDENT));
        StudentProfile engineer = studentProfileRepository.saveAndFlush(
                new StudentProfile(user, Program.COMPUTER_ENGINEERING, 2024));

        assertThat(service.unitLimitFor(priya)).isEqualByComparingTo("18");
        assertThat(service.unitLimitFor(engineer)).isEqualByComparingTo("22");
    }

    // #41: classes that overlap on the same day are found, and adding still works
    // (it only warns)
    @Test
    void findsOverlappingClassesButStillSaves() {
        Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20));
        Section math = saveLecture("MATH", "152", "4", DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0));
        service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);

        assertThat(service.addCourse(priya, math.getOffering().getId(), math.getId(), null)).isEmpty();
        assertThat(service.conflicts(priya)).hasSize(1);
        assertThat(service.conflicts(priya).get(0).first().course()).isEqualTo("MACM 201");
        assertThat(service.conflicts(priya).get(0).second().course()).isEqualTo("MATH 152");
    }

    // #41: back-to-back classes don't overlap
    @Test
    void backToBackClassesDontConflict() {
        Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20));
        Section math = saveLecture("MATH", "152", "4", DayOfWeek.MONDAY, LocalTime.of(11, 20), LocalTime.of(12, 20));
        service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);
        service.addCourse(priya, math.getOffering().getId(), math.getId(), null);

        assertThat(service.conflicts(priya)).isEmpty();
    }

    @Test
    void classIsPlacedAtItsDayAndTime() {
        Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20));
        service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);

        List<TimetableBlock> blocks = service.timetableBlocks(service.coursesFor(priya));

        assertThat(blocks).hasSize(1);
        TimetableBlock block = blocks.get(0);
        assertThat(block.column()).isEqualTo(2); // Monday
        assertThat(block.rowStart()).isEqualTo(17); // 10:30
        assertThat(block.rowEnd()).isEqualTo(22); // 11:20
        assertThat(block.label()).isEqualTo("MACM 201 D100 LEC");
        assertThat(block.conflict()).isFalse();
        assertThat(block.time()).contains("10:30");
    }

    @Test
    void overlappingClassesAreMarkedAsConflicts() {
        Section math = saveLecture("MATH", "152", "3", DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20));
        Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(11, 50));
        service.addCourse(priya, math.getOffering().getId(), math.getId(), null);
        service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);

        List<TimetableBlock> blocks = service.timetableBlocks(service.coursesFor(priya));

        assertThat(blocks).hasSize(2);
        assertThat(blocks).allMatch(TimetableBlock::conflict);
    }

    @Test
    void eachCourseGetsItsOwnColour() {
    Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(9, 30), LocalTime.of(10, 20));
    Section math = saveLecture("MATH", "152", "4", DayOfWeek.TUESDAY, LocalTime.of(9, 30), LocalTime.of(10, 20));
    service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);
    service.addCourse(priya, math.getOffering().getId(), math.getId(), null);

    List<TimetableBlock> blocks = service.timetableBlocks(service.coursesFor(priya));

    assertThat(blocks).extracting(TimetableBlock::color).containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void overlappingClassesSitSideBySide() {
    Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20));
    Section math = saveLecture("MATH", "152", "4", DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20));
    Section stat = saveLecture("STAT", "270", "3", DayOfWeek.MONDAY, LocalTime.of(13, 30), LocalTime.of(14, 20));
    service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);
    service.addCourse(priya, math.getOffering().getId(), math.getId(), null);
    service.addCourse(priya, stat.getOffering().getId(), stat.getId(), null);

    List<TimetableBlock> blocks = service.timetableBlocks(service.coursesFor(priya));

    TimetableBlock alone = blocks.stream().filter(b -> b.label().startsWith("STAT")).findFirst().orElseThrow();
    List<TimetableBlock> sharing = blocks.stream().filter(b -> !b.label().startsWith("STAT")).toList();

    assertThat(alone.lane()).isEqualTo(0);
    assertThat(alone.lanes()).isEqualTo(1);
    assertThat(sharing).extracting(TimetableBlock::lane).containsExactlyInAnyOrder(0, 1);
    assertThat(sharing).allMatch(b -> b.lanes() == 2);
   }

   @Test
    void gridStretchesForAnEarlyClass() {
        Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(7, 30), LocalTime.of(8, 20));
        service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);

        assertThat(service.gridHours(service.coursesFor(priya))).isEqualTo(new GridHours(7, 18));
        assertThat(service.timetableBlocks(service.coursesFor(priya)).get(0).rowStart()).isEqualTo(5);
    }

    private NextSemesterSchedule saveSchedule(StudentProfile student, String name, boolean main) {
        Term spring2027 = termRepository.findByTermYearAndSeason(2027, Season.SPRING).orElseThrow();
        return scheduleRepository.save(new NextSemesterSchedule(student, spring2027, name, main));
    }

    // #61: a course can go into any of the student's schedules, and the main one stays as it was
    @Test
    void addsACourseToTheScheduleItsGiven() {
        saveSchedule(priya, "My schedule", true);
        NextSemesterSchedule morning = saveSchedule(priya, "Morning labs", false);

        assertThat(service.addCourseTo(priya, morning.getId(), cmpt225.getId(), d100.getId(), d101.getId())).isEmpty();

        assertThat(service.coursesFor(morning)).hasSize(1);
        assertThat(service.coursesFor(priya)).isEmpty();
    }

    // #61: the schedule id comes from the form, so it can be changed to Marcus's
    @Test
    void cantAddToAnotherStudentsSchedule() {
        NextSemesterSchedule marcusSchedule = saveSchedule(marcus, "My schedule", true);

        assertThat(service.addCourseTo(priya, marcusSchedule.getId(), cmpt225.getId(), d100.getId(), d101.getId()))
                .containsExactly("That schedule isn't one of yours.");
        assertThat(service.coursesFor(marcusSchedule)).isEmpty();
        assertThat(service.mainSchedule(priya)).isEmpty();
    }

    // #61: units and overlaps are worked out from the courses given, so each schedule gets its own
    @Test
    void eachScheduleHasItsOwnUnitsAndOverlaps() {
        Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20));
        Section math = saveLecture("MATH", "152", "4", DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0));
        service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);
        service.addCourse(priya, math.getOffering().getId(), math.getId(), null);
        NextSemesterSchedule justMacm = saveSchedule(priya, "Just MACM", false);
        service.addCourseTo(priya, justMacm.getId(), macm.getOffering().getId(), macm.getId(), null);

        List<ScheduledCourse> mainCourses = service.coursesFor(priya);
        List<ScheduledCourse> justMacmCourses = service.coursesFor(justMacm);

        assertThat(service.totalUnits(mainCourses)).isEqualByComparingTo("7");
        assertThat(service.conflicts(mainCourses)).hasSize(1);
        assertThat(service.totalUnits(justMacmCourses)).isEqualByComparingTo("3");
        assertThat(service.conflicts(justMacmCourses)).isEmpty();
    }

    // A Mon/Wed lecture that only clashes on Monday is only outlined on Monday
    @Test
    void clashOnlyOutlinesTheDayItHappens() {
        Section macm = saveLecture("MACM", "201", "3", DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20));
        macm.replaceMeetings(List.of(
                new SectionMeeting(DayOfWeek.MONDAY, LocalTime.of(10, 30), LocalTime.of(11, 20), null),
                new SectionMeeting(DayOfWeek.WEDNESDAY, LocalTime.of(10, 30), LocalTime.of(11, 20), null)));
        sectionRepository.save(macm);
        Section math = saveLecture("MATH", "152", "4", DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(11, 50));
        service.addCourse(priya, macm.getOffering().getId(), macm.getId(), null);
        service.addCourse(priya, math.getOffering().getId(), math.getId(), null);

        List<TimetableBlock> blocks = service.timetableBlocks(service.coursesFor(priya));

        TimetableBlock wednesday = blocks.stream().filter(b -> b.column() == 4).findFirst().orElseThrow();
        assertThat(wednesday.conflict()).isFalse();
        assertThat(blocks.stream().filter(b -> b.column() == 2)).allMatch(TimetableBlock::conflict);
    }
}
