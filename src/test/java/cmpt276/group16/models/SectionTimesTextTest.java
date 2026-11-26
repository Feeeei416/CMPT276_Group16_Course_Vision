package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import cmpt276.group16.services.NextSemesterScheduleService.ClassTime;

// How section times read on My schedule: "Mon, Wed, Fri 10:30–11:20 (Burnaby)", not "MONDAY 10:30-11:20 Burnaby"
class SectionTimesTextTest {

    private static SectionMeeting meeting(DayOfWeek day, String start, String end, String campus) {
        return new SectionMeeting(day, LocalTime.parse(start), LocalTime.parse(end), campus);
    }

    private static Section section(SectionMeeting... meetings) {
        Section section = new Section(null, "D100", SectionType.LEC, true, null);
        section.replaceMeetings(List.of(meetings));
        return section;
    }

    @Test
    void oneMeetingReadsAsShortDayAndTime() {
        SectionMeeting meeting = meeting(DayOfWeek.TUESDAY, "10:30", "12:20", "Burnaby");

        assertThat(meeting.getShowText()).isEqualTo("Tue 10:30–12:20");
    }

    @Test
    void earlyTimesHaveNoLeadingZero() {
        assertThat(meeting(DayOfWeek.MONDAY, "08:30", "09:20", null).getShowText()).isEqualTo("Mon 8:30–9:20");
    }

    @Test
    void daysWithTheSameTimeAreWrittenTogether() {
        Section lecture = section(
                meeting(DayOfWeek.MONDAY, "10:30", "11:20", "Burnaby"),
                meeting(DayOfWeek.WEDNESDAY, "10:30", "11:20", "Burnaby"),
                meeting(DayOfWeek.FRIDAY, "10:30", "11:20", "Burnaby"));

        assertThat(lecture.getTimesText()).isEqualTo("Mon, Wed, Fri 10:30–11:20 (Burnaby)");
    }

    // Days are listed Monday first, whatever order SFU sent them in
    @Test
    void daysAreInWeekOrder() {
        Section lecture = section(
                meeting(DayOfWeek.THURSDAY, "14:30", "15:20", null),
                meeting(DayOfWeek.TUESDAY, "14:30", "15:20", null));

        assertThat(lecture.getTimesText()).isEqualTo("Tue, Thu 14:30–15:20");
    }

    @Test
    void differentTimesAreSeparateParts() {
        Section lecture = section(
                meeting(DayOfWeek.MONDAY, "10:30", "11:20", "Burnaby"),
                meeting(DayOfWeek.WEDNESDAY, "10:30", "11:20", "Burnaby"),
                meeting(DayOfWeek.FRIDAY, "13:30", "15:20", "Burnaby"));

        assertThat(lecture.getTimesText()).isEqualTo("Mon, Wed 10:30–11:20 (Burnaby); Fri 13:30–15:20 (Burnaby)");
    }

    @Test
    void sameTimeOnAnotherCampusIsItsOwnPart() {
        Section lecture = section(
                meeting(DayOfWeek.MONDAY, "10:30", "11:20", "Burnaby"),
                meeting(DayOfWeek.WEDNESDAY, "10:30", "11:20", "Surrey"));

        assertThat(lecture.getTimesText()).isEqualTo("Mon 10:30–11:20 (Burnaby); Wed 10:30–11:20 (Surrey)");
    }

    @Test
    void sectionWithoutTimesIsBlank() {
        assertThat(section().getTimesText()).isEmpty();
    }

    @Test
    void classTimeInTheWeekListHasNoLeadingZero() {
        ClassTime lab = new ClassTime("CMPT 225", "D101", SectionType.LAB, LocalTime.of(8, 30), LocalTime.of(9, 20));

        assertThat(lab.timeText()).isEqualTo("8:30–9:20");
    }
}
