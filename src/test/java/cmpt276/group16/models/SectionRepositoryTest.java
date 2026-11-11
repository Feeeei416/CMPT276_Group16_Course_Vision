package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class SectionRepositoryTest {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Term spring;

    // CMPT 225 in 2027 Spring, the offering most tests add sections to
    private CourseOffering cmpt225;

    @BeforeEach
    void setUp() {
        spring = termRepository.saveAndFlush(new Term(2027, Season.SPRING));
        cmpt225 = saveOffering("CMPT", "225", spring);
    }

    private CourseOffering saveOffering(String subject, String number, Term term) {
        Course course = courseRepository.saveAndFlush(new Course(subject, number, "Some Course",
                new BigDecimal("3.00"), null, null, null));
        return courseOfferingRepository.saveAndFlush(new CourseOffering(course, term, true));
    }

    private Section lecture(CourseOffering offering, String code) {
        return new Section(offering, code, SectionType.LEC, true, null);
    }

    private SectionMeeting meeting(DayOfWeek day, String start, String end) {
        return new SectionMeeting(day, LocalTime.parse(start), LocalTime.parse(end), "Burnaby");
    }

    // A saved section can be loaded back with its offering, type and meetings in the order they were added
    @Test
    void sectionIsSavedWithItsMeetings() {
        Section section = lecture(cmpt225, "D100");
        section.replaceMeetings(List.of(
                meeting(DayOfWeek.MONDAY, "10:30", "11:20"),
                meeting(DayOfWeek.WEDNESDAY, "10:30", "11:20"),
                meeting(DayOfWeek.FRIDAY, "10:30", "11:20")));
        sectionRepository.saveAndFlush(section);
        entityManager.clear();

        Section found = sectionRepository
                .findByOfferingIdAndSectionCode(cmpt225.getId(), "D100").orElseThrow();
        assertThat(found.getOffering().getCourse().getSubject()).isEqualTo("CMPT");
        assertThat(found.getOffering().getTerm().getSeason()).isEqualTo(Season.SPRING);
        assertThat(found.getSectionType()).isEqualTo(SectionType.LEC);
        assertThat(found.isPrimarySection()).isTrue();
        assertThat(found.getAssociatedClass()).isNull();
        assertThat(found.getFetchedAt()).isNotNull();
        assertThat(found.getMeetings()).containsExactly(
                meeting(DayOfWeek.MONDAY, "10:30", "11:20"),
                meeting(DayOfWeek.WEDNESDAY, "10:30", "11:20"),
                meeting(DayOfWeek.FRIDAY, "10:30", "11:20"));
    }

    // A section without times (for example online or TBA) is saved with no meetings
    @Test
    void sectionWithoutMeetingsIsSaved() {
        sectionRepository.saveAndFlush(lecture(cmpt225, "D100"));
        entityManager.clear();

        Section found = sectionRepository
                .findByOfferingIdAndSectionCode(cmpt225.getId(), "D100").orElseThrow();
        assertThat(found.getMeetings()).isEmpty();
    }

    // A tutorial keeps which primary section it belongs to
    @Test
    void nonPrimarySectionKeepsAssociatedClass() {
        sectionRepository.saveAndFlush(new Section(cmpt225, "D101", SectionType.TUT, false, "1"));
        entityManager.clear();

        Section found = sectionRepository
                .findByOfferingIdAndSectionCode(cmpt225.getId(), "D101").orElseThrow();
        assertThat(found.getSectionType()).isEqualTo(SectionType.TUT);
        assertThat(found.isPrimarySection()).isFalse();
        assertThat(found.getAssociatedClass()).isEqualTo("1");
    }

    // The same offering can't have two sections with the same code
    @Test
    void rejectsDuplicateSectionInOneOffering() {
        sectionRepository.saveAndFlush(lecture(cmpt225, "D100"));

        assertThatThrownBy(() -> sectionRepository.saveAndFlush(lecture(cmpt225, "d100")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // The unique rule is on offering and code together, so D100 can exist in many offerings
    @Test
    void allowsSameCodeInDifferentOfferings() {
        CourseOffering cmpt276 = saveOffering("CMPT", "276", spring);
        sectionRepository.saveAndFlush(lecture(cmpt225, "D100"));
        sectionRepository.saveAndFlush(lecture(cmpt276, "D100"));

        assertThat(sectionRepository.count()).isEqualTo(2);
    }

    // A section must belong to an offering
    @Test
    void rejectsMissingOffering() {
        assertThatThrownBy(() -> sectionRepository.saveAndFlush(lecture(null, "D100")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Codes are stored uppercase, so the normalized lookup finds a section from the lowercase API value
    @Test
    void normalizedLookupIgnoresCase() {
        sectionRepository.saveAndFlush(lecture(cmpt225, " d100 "));
        entityManager.clear();

        assertThat(sectionRepository.findByOfferingIdAndNormalizedSectionCode(cmpt225.getId(), "d100"))
                .map(Section::getSectionCode).contains("D100");
        assertThat(sectionRepository.findByOfferingIdAndSectionCode(cmpt225.getId(), "D200")).isEmpty();
    }

    // An offering's sections come back sorted by code, and only for that offering
    @Test
    void findsSectionsOfOneOfferingSortedByCode() {
        CourseOffering cmpt276 = saveOffering("CMPT", "276", spring);
        sectionRepository.saveAndFlush(new Section(cmpt225, "D102", SectionType.TUT, false, "1"));
        sectionRepository.saveAndFlush(lecture(cmpt225, "D100"));
        sectionRepository.saveAndFlush(new Section(cmpt225, "D101", SectionType.TUT, false, "1"));
        sectionRepository.saveAndFlush(lecture(cmpt276, "D100"));
        entityManager.clear();

        assertThat(sectionRepository.findByOfferingIdOrderBySectionCodeAsc(cmpt225.getId()))
                .extracting(Section::getSectionCode)
                .containsExactly("D100", "D101", "D102");
    }

    // A re-fetch replaces the old meetings, and the old rows are removed from the database
    @Test
    void replaceMeetingsRemovesOldMeetings() {
        Section section = lecture(cmpt225, "D100");
        section.replaceMeetings(List.of(
                meeting(DayOfWeek.MONDAY, "10:30", "11:20"),
                meeting(DayOfWeek.WEDNESDAY, "10:30", "11:20")));
        Section saved = sectionRepository.saveAndFlush(section);
        entityManager.clear();

        Section found = sectionRepository.findById(saved.getId()).orElseThrow();
        found.replaceMeetings(List.of(meeting(DayOfWeek.TUESDAY, "14:30", "16:20")));
        found.markFetched();
        sectionRepository.saveAndFlush(found);
        entityManager.clear();

        assertThat(sectionRepository.findById(saved.getId()).orElseThrow().getMeetings())
                .containsExactly(meeting(DayOfWeek.TUESDAY, "14:30", "16:20"));
        Number rows = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM section_meetings")
                .getSingleResult();
        assertThat(rows.intValue()).isEqualTo(1);
    }

    // A meeting must have a day and times, and end after it starts
    @Test
    void meetingRejectsMissingOrBackwardsTimes() {
        assertThatThrownBy(() -> new SectionMeeting(null, LocalTime.of(10, 30), LocalTime.of(11, 20), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SectionMeeting(DayOfWeek.MONDAY, LocalTime.of(11, 20), LocalTime.of(10, 30), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}