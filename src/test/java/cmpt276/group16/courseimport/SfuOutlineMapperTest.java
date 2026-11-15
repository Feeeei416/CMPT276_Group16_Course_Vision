package cmpt276.group16.courseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import cmpt276.group16.courseimport.SfuOutlineMapper.CourseListing;
import cmpt276.group16.courseimport.SfuOutlineMapper.OutlineData;
import cmpt276.group16.courseimport.SfuOutlineMapper.SectionListing;
import cmpt276.group16.models.Designation;
import cmpt276.group16.models.SectionMeeting;
import cmpt276.group16.models.SectionType;

// Reads the real API responses saved in src/test/resources/sfu (Spring 2027)
class SfuOutlineMapperTest {
    
    private final SfuOutlineMapper mapper = new SfuOutlineMapper();

    private static String saved(String name) throws IOException {
        try (InputStream in = SfuOutlineMapperTest.class.getResourceAsStream("/sfu/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static SectionMeeting meeting(DayOfWeek day, String start, String end, String campus) {
        return new SectionMeeting(day, LocalTime.parse(start), LocalTime.parse(end), campus);
    }

    // A department's course list keeps each number as written, with its title
    @Test
    void readsCourseList() throws IOException {
        List<CourseListing> courses = mapper.readCourses(saved("cmpt-courses.json"));
 
        assertThat(courses).first().isEqualTo(new CourseListing("105W",
                "Social Issues and Communication Strategies in Computing Science"));
        assertThat(courses).contains(new CourseListing("225", "Data Structures and Programming"));
    }

    // Lectures are primary, labs aren't, and a lecture and its labs share an associatedClass
    @Test
    void readsSectionList() throws IOException {
        List<SectionListing> sections = mapper.readSections(saved("cmpt-225-sections.json"));
 
        assertThat(sections).hasSize(10);
        assertThat(sections.get(0)).isEqualTo(new SectionListing("D100", SectionType.LEC, true, "1"));
        assertThat(sections.get(1)).isEqualTo(new SectionListing("D101", SectionType.LAB, false, "1"));
        assertThat(sections.get(5)).isEqualTo(new SectionListing("D200", SectionType.LEC, true, "2"));
    }

    // MATH 152 has open labs ("OPL"), a type the API docs don't list
    @Test
    void unknownSectionTypeIsOther() throws IOException {
        List<SectionListing> sections = mapper.readSections(saved("math-152-sections.json"));
 
        assertThat(sections).filteredOn(s -> s.code().equals("OP01"))
                .containsExactly(new SectionListing("OP01", SectionType.OTHER, false, "200"));
    }

    // An outline gives the course details, and a section that meets at different times on two days
    // gets one meeting per day
    @Test
    void readsOutline() throws IOException {
        OutlineData outline = mapper.readOutline(saved("cmpt-225-d100.json"));
 
        assertThat(outline.title()).isEqualTo("Data Structures and Programming");
        assertThat(outline.units()).isEqualByComparingTo("3");
        assertThat(outline.description()).startsWith("Introduction to a variety");
        assertThat(outline.prereqText()).startsWith("(MACM 101 and (CMPT 125, CMPT 129 or CMPT 135))");
        assertThat(outline.coreqText()).isNull();
        assertThat(outline.designations()).containsExactly(Designation.Q);
        assertThat(outline.meetings()).containsExactly(
                meeting(DayOfWeek.TUESDAY, "10:30", "12:20", "Burnaby"),
                meeting(DayOfWeek.FRIDAY, "10:30", "11:20", "Burnaby"));
    }

    // A lab has its own outline with its own time
    @Test
    void readsLabOutline() throws IOException {
        OutlineData outline = mapper.readOutline(saved("cmpt-225-d101.json"));
 
        assertThat(outline.meetings()).containsExactly(meeting(DayOfWeek.MONDAY, "17:30", "18:20", "Burnaby"));
    }

    // "Mo, We, Fr" is three days, and the API writes 8:30 with a one-digit hour
    @Test
    void splitsDaysAndReadsOneDigitHours() throws IOException {
        OutlineData outline = mapper.readOutline(saved("math-152-d100.json"));
 
        assertThat(outline.meetings()).containsExactly(
                meeting(DayOfWeek.MONDAY, "08:30", "09:20", "Burnaby"),
                meeting(DayOfWeek.WEDNESDAY, "08:30", "09:20", "Burnaby"),
                meeting(DayOfWeek.FRIDAY, "08:30", "09:20", "Burnaby"));
    }

    // Several designations are joined with "/"
    @Test
    void readsSeveralDesignations() throws IOException {
        assertThat(mapper.readOutline(saved("cmpt-120-d100.json")).designations())
                .containsExactlyInAnyOrder(Designation.Q, Designation.B_SCI);
    }

    // The API sends "" when there are no prerequisites, which is stored as null
    @Test
    void emptyPrerequisitesAreNull() throws IOException {
        OutlineData outline = mapper.readOutline(saved("cmpt-105w-d100.json"));
 
        assertThat(outline.prereqText()).isNull();
        assertThat(outline.designations()).containsExactly(Designation.W);
    }

    // Exam rows, rows without times, and unknown days or designations are skipped instead of failing
    @Test
    void skipsRowsAndValuesItCantUse() {
        String json = """
                {"info": {"title": "Test", "units": "3", "designation": "Breadth-Social Sciences/Breadth-Humanities/Something new"},
                 "courseSchedule": [
                   {"days": "Th", "startTime": "9:00", "endTime": "12:00", "campus": "Burnaby", "isExam": true},
                   {"days": "", "startTime": "", "endTime": "", "campus": "", "isExam": false},
                   {"days": "Xx, We", "startTime": "14:30", "endTime": "15:20", "campus": "", "isExam": false}
                 ]}
                """;
 
        OutlineData outline = mapper.readOutline(json);
 
        assertThat(outline.meetings()).containsExactly(meeting(DayOfWeek.WEDNESDAY, "14:30", "15:20", null));
        assertThat(outline.designations()).containsExactlyInAnyOrder(Designation.B_SOC, Designation.B_HUM);
    }

    // Units that aren't a plain number come back as null, for the import to decide
    @Test
    void unitsThatArentANumberAreNull() {
        OutlineData outline = mapper.readOutline("{\"info\": {\"title\": \"Test\", \"units\": \"0-4\"}}");
 
        assertThat(outline.units()).isNull();
        assertThat(outline.meetings()).isEmpty();
    }
 
    // Text that isn't the JSON the API normally sends stops the import
    @Test
    void unreadableJsonThrows() {
        assertThatThrownBy(() -> mapper.readSections("<html>Service unavailable</html>"))
                .isInstanceOf(SfuApiException.class);
        assertThatThrownBy(() -> mapper.readOutline("{\"courseSchedule\": []}"))
                .isInstanceOf(SfuApiException.class);
    }
}
