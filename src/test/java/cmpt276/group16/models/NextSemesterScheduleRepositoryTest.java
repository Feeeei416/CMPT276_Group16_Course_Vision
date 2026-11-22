package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

// Covers the next_semester_schedule and scheduled_courses tables together, for keeping several schedules (#61)
@DataJpaTest
class NextSemesterScheduleRepositoryTest {

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

    private Term spring2027;
    private Term fall2026;
    private StudentProfile jack;
    private StudentProfile finn;

    @BeforeEach
    void setUp() {
        spring2027 = termRepository.saveAndFlush(new Term(2027, Season.SPRING));
        fall2026 = termRepository.saveAndFlush(new Term(2026, Season.FALL));
        jack = saveStudent("jack@example.com");
        finn = saveStudent("finn@example.com");
    }

    private StudentProfile saveStudent(String email) {
        User user = userRepository.saveAndFlush(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.saveAndFlush(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private NextSemesterSchedule saveSchedule(StudentProfile student, Term term, String name, boolean main) {
        return scheduleRepository.saveAndFlush(new NextSemesterSchedule(student, term, name, main));
    }

    // A course in 2027 Spring with one lecture, reused when it already exists, put in the schedule
    private ScheduledCourse saveCourse(NextSemesterSchedule schedule, String subject, String number) {
        Course course = courseRepository.findBySubjectAndNumber(subject, number)
                .orElseGet(() -> courseRepository.saveAndFlush(new Course(subject, number,
                        "Title of " + subject + " " + number, new BigDecimal("3.00"), null, null, null)));
        CourseOffering offering = courseOfferingRepository.saveAndFlush(new CourseOffering(course, spring2027, true));
        Section lecture = sectionRepository.saveAndFlush(new Section(offering, "D100", SectionType.LEC, true, "1"));
        return scheduledCourseRepository.saveAndFlush(new ScheduledCourse(schedule, offering, lecture, null));
    }

    @Test
    void findsEveryScheduleOfOneStudentForOneTermInTheOrderTheyWereMade() {
        saveSchedule(jack, spring2027, "My schedule", true);
        saveSchedule(jack, spring2027, "Morning labs", false);
        saveSchedule(jack, fall2026, "Last term", true);
        saveSchedule(finn, spring2027, "My schedule", true);

        assertThat(scheduleRepository.findByStudentAndTermOrderByIdAsc(jack, spring2027))
                .extracting(NextSemesterSchedule::getName)
                .containsExactly("My schedule", "Morning labs");
    }

    @Test
    void anotherStudentCantFindMySchedule() {
        NextSemesterSchedule schedule = saveSchedule(jack, spring2027, "My schedule", true);

        assertThat(scheduleRepository.findByIdAndStudentAndTerm(schedule.getId(), jack, spring2027)).isPresent();
        assertThat(scheduleRepository.findByIdAndStudentAndTerm(schedule.getId(), finn, spring2027)).isEmpty();
    }

    // A schedule from a past term isn't listed, so it can't be opened by its id either
    @Test
    void scheduleForAnotherTermIsntFound() {
        NextSemesterSchedule old = saveSchedule(jack, fall2026, "My schedule", true);

        assertThat(scheduleRepository.findByIdAndStudentAndTerm(old.getId(), jack, spring2027)).isEmpty();
    }

    @Test
    void scheduleNamesAreCheckedIgnoringCase() {
        saveSchedule(jack, spring2027, "My schedule", true);

        assertThat(scheduleRepository.existsByStudentAndTermAndNameIgnoreCase(jack, spring2027, "MY SCHEDULE"))
                .isTrue();
        assertThat(scheduleRepository.existsByStudentAndTermAndNameIgnoreCase(jack, spring2027, "Morning labs"))
                .isFalse();
        assertThat(scheduleRepository.existsByStudentAndTermAndNameIgnoreCase(jack, fall2026, "My schedule"))
                .isFalse();
        assertThat(scheduleRepository.existsByStudentAndTermAndNameIgnoreCase(finn, spring2027, "My schedule"))
                .isFalse();
    }

    // The safety net under the service's lock: a student can't end up with two schedules called "My schedule"
    @Test
    void rejectsTwoSchedulesWithTheSameNameInOneTerm() {
        saveSchedule(jack, spring2027, "My schedule", true);

        assertThatThrownBy(() -> saveSchedule(jack, spring2027, "My schedule", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void renamingAndChangingTheMainScheduleAreSaved() {
        NextSemesterSchedule main = saveSchedule(jack, spring2027, "My schedule", true);
        NextSemesterSchedule other = saveSchedule(jack, spring2027, "Morning labs", false);

        main.setMain(false);
        other.setMain(true);
        other.setName("Afternoon labs");
        entityManager.flush();
        entityManager.clear();

        assertThat(scheduleRepository.findByStudentAndTermAndMainTrue(jack, spring2027))
                .get()
                .extracting(NextSemesterSchedule::getName)
                .isEqualTo("Afternoon labs");
    }

    @Test
    void deletingAScheduleCoursesLeavesOtherSchedulesAlone() {
        NextSemesterSchedule main = saveSchedule(jack, spring2027, "My schedule", true);
        NextSemesterSchedule morning = saveSchedule(jack, spring2027, "Morning labs", false);
        saveCourse(main, "CMPT", "225");
        saveCourse(morning, "CMPT", "276");
        saveCourse(morning, "MACM", "201");
        assertThat(scheduledCourseRepository.countBySchedule(morning)).isEqualTo(2);

        assertThat(scheduledCourseRepository.deleteBySchedule(morning)).isEqualTo(2);
        scheduleRepository.delete(morning);
        entityManager.flush();
        entityManager.clear();

        assertThat(scheduleRepository.findByStudentAndTermOrderByIdAsc(jack, spring2027))
                .extracting(NextSemesterSchedule::getName)
                .containsExactly("My schedule");
        assertThat(scheduledCourseRepository.countBySchedule(main)).isEqualTo(1);
    }
}
