package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.CourseOfferingRepository;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.NextSemesterSchedule;
import cmpt276.group16.models.NextSemesterScheduleRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.ScheduledCourse;
import cmpt276.group16.models.ScheduledCourseRepository;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.Section;
import cmpt276.group16.models.SectionRepository;
import cmpt276.group16.models.SectionType;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.ScheduleListService.ScheduleSummary;

// @DataJpaTest only loads repositories, so the services are built by hand with a fixed date.
// On November 9, 2026, next semester is 2027 Spring
@DataJpaTest
class ScheduleListServiceTest {

    private static final Clock NOV_9_2026 = Clock.fixed(Instant.parse("2026-11-09T20:00:00Z"),
            ZoneId.of("America/Vancouver"));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private NextSemesterScheduleRepository scheduleRepository;

    @Autowired
    private ScheduledCourseRepository scheduledCourseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private NextSemesterScheduleService scheduleService;
    private ScheduleListService scheduleListService;

    private Term spring2027;
    private Term fall2026;
    private StudentProfile jack;
    private StudentProfile finn;

    @BeforeEach
    void setUp() {
        scheduleService = scheduleServiceOn(NOV_9_2026);
        scheduleListService = new ScheduleListService(scheduleRepository, scheduledCourseRepository,
                studentProfileRepository, scheduleService);
        spring2027 = termRepository.saveAndFlush(new Term(2027, Season.SPRING));
        fall2026 = termRepository.saveAndFlush(new Term(2026, Season.FALL));
        jack = saveStudent("jack@example.com");
        finn = saveStudent("finn@example.com");
    }

    private NextSemesterScheduleService scheduleServiceOn(Clock clock) {
        return new NextSemesterScheduleService(scheduleRepository, scheduledCourseRepository, sectionRepository,
                termRepository, courseOfferingRepository, studentProfileRepository, clock);
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private NextSemesterSchedule saveSchedule(StudentProfile student, String name, boolean main) {
        return scheduleRepository.saveAndFlush(new NextSemesterSchedule(student, spring2027, name, main));
    }

    // A course in 2027 Spring with lecture D100 and tutorial D101, reused when it already exists
    private CourseOffering offering(String subject, String number) {
        return courseOfferingRepository.findByTermIdAndCourseSubjectAndCourseNumber(spring2027.getId(), subject, number)
                .orElseGet(() -> {
                    Course course = courseRepository.saveAndFlush(new Course(subject, number,
                            "Title of " + subject + " " + number, new BigDecimal("3.00"), null, null, null));
                    CourseOffering offering = courseOfferingRepository.saveAndFlush(
                            new CourseOffering(course, spring2027, true));
                    sectionRepository.saveAndFlush(new Section(offering, "D100", SectionType.LEC, true, "1"));
                    sectionRepository.saveAndFlush(new Section(offering, "D101", SectionType.TUT, false, "1"));
                    return offering;
                });
    }

    private Section section(CourseOffering offering, String code) {
        return sectionRepository.findByOfferingIdOrderBySectionCodeAsc(offering.getId()).stream()
                .filter(section -> section.getSectionCode().equals(code)).findFirst().orElseThrow();
    }

    private void saveCourse(NextSemesterSchedule schedule, String subject, String number) {
        CourseOffering offering = offering(subject, number);
        scheduledCourseRepository.saveAndFlush(new ScheduledCourse(schedule, offering, section(offering, "D100"),
                section(offering, "D101")));
    }

    // Reads the student's schedules back from the database, main schedule first
    private List<ScheduleSummary> schedulesOf(StudentProfile student) {
        entityManager.flush();
        entityManager.clear();
        return scheduleListService.schedules(student);
    }

    private NextSemesterSchedule scheduleNamed(StudentProfile student, String name) {
        return schedulesOf(student).stream().map(ScheduleSummary::schedule)
                .filter(schedule -> schedule.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void listsTheMainScheduleFirstThenTheRestInTheOrderTheyWereMade() {
        saveSchedule(jack, "Morning labs", false);
        saveSchedule(jack, "My schedule", true);
        saveSchedule(jack, "Afternoon labs", false);
        saveSchedule(finn, "Finn's schedule", true);

        assertThat(schedulesOf(jack)).extracting(summary -> summary.schedule().getName())
            .containsExactly("My schedule", "Morning labs", "Afternoon labs");
    }

    @Test
    void onlyNextSemestersSchedulesAreListed() {
        saveSchedule(jack, "My schedule", true);
        scheduleRepository.saveAndFlush(new NextSemesterSchedule(jack, fall2026, "Last term", true));

        assertThat(schedulesOf(jack)).extracting(summary -> summary.schedule().getName())
            .containsExactly("My schedule");
    }

    @Test
    void eachScheduleSaysHowManyCoursesItHas() {
        NextSemesterSchedule main = saveSchedule(jack, "My schedule", true);
        saveCourse(main, "CMPT", "225");
        saveCourse(main, "CMPT", "276");
        saveCourse(saveSchedule(jack, "One course", false), "CMPT", "225");
        saveSchedule(jack, "Empty", false);

        assertThat(schedulesOf(jack)).extracting(ScheduleSummary::sizeText)
            .containsExactly("2 courses", "1 course", "0 courses");
    }

    @Test
    void firstScheduleBecomesTheMainSchedule() {
        PlanChange change = scheduleListService.create(jack, "  Morning labs ", null);

        assertThat(change.message()).isEqualTo("Made Morning labs.");
        assertThat(schedulesOf(jack))
            .extracting(summary -> summary.schedule().getName(), summary -> summary.schedule().isMain())
            .containsExactly(tuple("Morning labs", true));
    }

    @Test
    void laterSchedulesStartEmptyAndArentMain() {
        saveSchedule(jack, "My schedule", true);

        scheduleListService.create(jack, "Morning labs", null);

        NextSemesterSchedule morning = scheduleNamed(jack, "Morning labs");
        assertThat(morning.isMain()).isFalse();
        assertThat(morning.getTerm().getId()).isEqualTo(spring2027.getId());
        assertThat(scheduledCourseRepository.countBySchedule(morning)).isZero();
    }

    @Test
    void copyHasTheSameCoursesAndSections() {
        NextSemesterSchedule main = saveSchedule(jack, "My schedule", true);
        saveCourse(main, "CMPT", "225");
        saveCourse(main, "CMPT", "276");

        PlanChange change = scheduleListService.create(jack, "Morning labs", main.getId());

        assertThat(change.message()).isEqualTo("Made Morning labs as a copy of My schedule.");
        NextSemesterSchedule copy = scheduleNamed(jack, "Morning labs");
        assertThat(copy.isMain()).isFalse();
        assertThat(scheduledCourseRepository.findBySchedule(copy))
            .extracting(course -> course.getOffering().getCourse().getNumber(),
                    course -> course.getPrimarySection().getSectionCode(),
                    course -> course.getAttachedSection().getSectionCode())
            .containsExactlyInAnyOrder(tuple("225", "D100", "D101"), tuple("276", "D100", "D101"));
    }

    // Changing the copy afterwards mustn't change the schedule it was copied from
    @Test
    void copyIsSeparateFromTheOriginal() {
        NextSemesterSchedule main = saveSchedule(jack, "My schedule", true);
        saveCourse(main, "CMPT", "225");
        scheduleListService.create(jack, "Morning labs", main.getId());

        scheduleListService.delete(jack, scheduleNamed(jack, "Morning labs").getId());

        assertThat(schedulesOf(jack)).extracting(ScheduleSummary::sizeText).containsExactly("1 course");
    }

    @Test
    void cantCopyAnotherStudentsSchedule() {
        NextSemesterSchedule finnSchedule = saveSchedule(finn, "Finn's schedule", true);
        saveSchedule(jack, "My schedule", true);

        PlanChange change = scheduleListService.create(jack, "Stolen", finnSchedule.getId());

        assertThat(change.problems()).containsExactly("The schedule to copy isn't one of your schedules.");
        assertThat(schedulesOf(jack)).hasSize(1);
    }

    @Test
    void cantUseANameTheStudentAlreadyHasInAnyCase() {
        saveSchedule(jack, "Morning labs", true);

        PlanChange change = scheduleListService.create(jack, "MORNING LABS", null);

        assertThat(change.problems()).containsExactly("You already have a schedule called MORNING LABS.");
    }

    @Test
    void anotherStudentCanUseTheSameName() {
        saveSchedule(finn, "Morning labs", true);

        assertThat(scheduleListService.create(jack, "Morning labs", null).isSaved()).isTrue();
    }

    // Names only have to be different within a term, so every term can have its own "My schedule"
    @Test
    void lastTermsNamesCanBeUsedAgain() {
        scheduleRepository.saveAndFlush(new NextSemesterSchedule(jack, fall2026, "My schedule", true));

        assertThat(scheduleListService.create(jack, "My schedule", null).isSaved()).isTrue();
    }

    @Test
    void rejectsABlankName() {
        assertThat(scheduleListService.create(jack, "   ", null).problems())
            .containsExactly("Give the schedule a name.");
        assertThat(schedulesOf(jack)).isEmpty();
    }

    // In Summer 2027 next semester is 2027 Fall, which hasn't been imported in these tests
    @Test
    void nothingCanBeMadeBeforeNextSemestersCoursesAreLoaded() {
        Clock june2027 = Clock.fixed(Instant.parse("2027-06-01T20:00:00Z"), ZoneId.of("America/Vancouver"));
        ScheduleListService later = new ScheduleListService(scheduleRepository, scheduledCourseRepository,
                studentProfileRepository, scheduleServiceOn(june2027));

        assertThat(later.create(jack, "Morning labs", null).problems())
            .containsExactly("Next semester's courses aren't available yet.");
        assertThat(later.schedules(jack)).isEmpty();
    }

    @Test
    void renamesASchedule() {
        NextSemesterSchedule schedule = saveSchedule(jack, "My schedule", true);

        PlanChange change = scheduleListService.rename(jack, schedule.getId(), " Morning labs ");

        assertThat(change.message()).isEqualTo("Renamed My schedule to Morning labs.");
        assertThat(scheduleNamed(jack, "Morning labs").isMain()).isTrue();
    }

    @Test
    void renamingCanFixOnlyTheCapitals() {
        NextSemesterSchedule schedule = saveSchedule(jack, "morning labs", true);

        assertThat(scheduleListService.rename(jack, schedule.getId(), "Morning labs").isSaved()).isTrue();
        assertThat(scheduleNamed(jack, "Morning labs")).isNotNull();
    }

    @Test
    void cantRenameToAnotherSchedulesName() {
        saveSchedule(jack, "My schedule", true);
        NextSemesterSchedule morning = saveSchedule(jack, "Morning labs", false);

        assertThat(scheduleListService.rename(jack, morning.getId(), "my schedule").problems())
            .containsExactly("You already have a schedule called my schedule.");
    }

    @Test
    void renamingToTheSameNameSaysSo() {
        NextSemesterSchedule schedule = saveSchedule(jack, "My schedule", true);

        assertThat(scheduleListService.rename(jack, schedule.getId(), "My schedule").problems())
            .containsExactly("My schedule already has that name.");
    }

    @Test
    void deletesAScheduleWithItsCourses() {
        NextSemesterSchedule main = saveSchedule(jack, "My schedule", true);
        NextSemesterSchedule morning = saveSchedule(jack, "Morning labs", false);
        saveCourse(main, "CMPT", "225");
        saveCourse(morning, "CMPT", "225");
        saveCourse(morning, "CMPT", "276");

        PlanChange change = scheduleListService.delete(jack, morning.getId());

        assertThat(change.message()).isEqualTo("Deleted Morning labs.");
        assertThat(schedulesOf(jack)).extracting(summary -> summary.schedule().getName())
            .containsExactly("My schedule");
        assertThat(scheduledCourseRepository.count()).isEqualTo(1);
    }

    // There's always a main schedule for My schedule to show
    @Test
    void cantDeleteTheMainSchedule() {
        NextSemesterSchedule main = saveSchedule(jack, "My schedule", true);

        assertThat(scheduleListService.delete(jack, main.getId()).problems())
            .containsExactly("My schedule is your main schedule. Make another schedule your main schedule "
                    + "before deleting it.");
        assertThat(schedulesOf(jack)).hasSize(1);
    }

    @Test
    void makingAScheduleMainUnmarksTheOldOne() {
        saveSchedule(jack, "My schedule", true);
        NextSemesterSchedule morning = saveSchedule(jack, "Morning labs", false);

        PlanChange change = scheduleListService.makeMain(jack, morning.getId());

        assertThat(change.message()).isEqualTo("Morning labs is now your main schedule.");
        assertThat(schedulesOf(jack))
            .extracting(summary -> summary.schedule().getName(), summary -> summary.schedule().isMain())
            .containsExactly(tuple("Morning labs", true),
                    tuple("My schedule", false));
    }

    // My schedule shows the main schedule, so after switching, a course added there goes into the new one
    @Test
    void addingACourseAfterSwitchingGoesIntoTheNewMainSchedule() {
        saveSchedule(jack, "My schedule", true);
        NextSemesterSchedule morning = saveSchedule(jack, "Morning labs", false);
        scheduleListService.makeMain(jack, morning.getId());
        CourseOffering cmpt225 = offering("CMPT", "225");

        assertThat(scheduleService.addCourse(jack, cmpt225.getId(), section(cmpt225, "D100").getId(),
                section(cmpt225, "D101").getId())).isEmpty();

        assertThat(schedulesOf(jack))
            .extracting(summary -> summary.schedule().getName(), ScheduleSummary::sizeText)
            .containsExactly(tuple("Morning labs", "1 course"),
                    tuple("My schedule", "0 courses"));
    }

    @Test
    void makingTheMainScheduleMainAgainSaysSo() {
        NextSemesterSchedule main = saveSchedule(jack, "My schedule", true);

        assertThat(scheduleListService.makeMain(jack, main.getId()).problems())
            .containsExactly("My schedule is already your main schedule.");
    }

    // Changing the id in the request finds nothing, and Finn's schedule stays as it was
    @Test
    void cantChangeAnotherStudentsSchedule() {
        saveSchedule(finn, "Finn's schedule", true);
        NextSemesterSchedule finnMorning = saveSchedule(finn, "Finn's morning labs", false);
        saveSchedule(jack, "My schedule", true);
        Long id = finnMorning.getId();

        assertThat(scheduleListService.rename(jack, id, "Mine now").problems())
            .containsExactly("That schedule isn't one of yours.");
        assertThat(scheduleListService.makeMain(jack, id).problems())
            .containsExactly("That schedule isn't one of yours.");
        assertThat(scheduleListService.delete(jack, id).problems())
            .containsExactly("That schedule isn't one of yours.");
        assertThat(scheduleListService.find(jack, id)).isEmpty();
        assertThat(schedulesOf(finn)).extracting(summary -> summary.schedule().getName())
            .containsExactly("Finn's schedule", "Finn's morning labs");
    }

    // Last term's schedules aren't listed, so they can't be opened or changed by their id either
    @Test
    void cantOpenOrChangeLastTermsSchedule() {
        saveSchedule(jack, "My schedule", true);
        NextSemesterSchedule old = scheduleRepository.saveAndFlush(
                new NextSemesterSchedule(jack, fall2026, "Last term", false));

        assertThat(scheduleListService.find(jack, old.getId())).isEmpty();
        assertThat(scheduleListService.delete(jack, old.getId()).problems())
            .containsExactly("That schedule isn't one of yours.");
        assertThat(scheduleRepository.findById(old.getId())).isPresent();
    }
}
