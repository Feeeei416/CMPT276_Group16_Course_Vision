package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.services.CourseOptions.Option;

@DataJpaTest
class CourseOptionsTest {

    @Autowired
    private CourseRepository courseRepository;

    private CourseOptions courseOptions;

    @BeforeEach
    void setUp() {
        courseOptions = new CourseOptions(courseRepository);
    }

    private Course save(String subject, String number, String title, String units) {
        return courseRepository.saveAndFlush(
                new Course(subject, number, title, new BigDecimal(units), null, null, null));
    }

    @Test
    void everyCourseIsAnOptionInCodeOrder() {
        save("MATH", "151", "Calculus I", "3.00");
        save("CMPT", "225", "Data Structures and Programming", "3.00");
        save("CMPT", "201", "Systems Programming", "4.00");

        List<Option> options = courseOptions.allCourses();

        assertThat(options).extracting(Option::code).containsExactly("CMPT 201", "CMPT 225", "MATH 151");
        assertThat(options.get(0)).isEqualTo(new Option("CMPT 201", "CMPT", "201", "Systems Programming", "4"));
    }

    @Test
    void jsonHasEachCoursesCodeTitleAndUnits() {
        String json = CourseOptions.json(List.of(new Option("CMPT 225", "CMPT", "225", "Data Structures", "3")));

        assertThat(json).isEqualTo("[{\"code\":\"CMPT 225\",\"subject\":\"CMPT\",\"number\":\"225\","
                + "\"title\":\"Data Structures\",\"units\":\"3\"}]");
    }

    // The JSON goes inside a <script> block, so a title can't be allowed to close it early
    @Test
    void jsonCantEndTheScriptBlock() {
        String json = CourseOptions.json(List.of(new Option("CMPT 999", "CMPT", "999", "</script><b>", "3")));

        assertThat(json).doesNotContain("</").contains("<\\/script>");
    }

    @Test
    void noteWhenTheUnitsDontMatchWhatSfuListsNow() {
        save("ENSC", "204", "Graphical Communication for Engineering", "3.00");

        // ENSC 204 was 1 unit in the 2023 Calendar
        assertThat(courseOptions.unitsNote("ENSC", "204", new BigDecimal("1.00")))
                .contains("SFU currently lists ENSC 204 as 3 units. Check your transcript if yours is different.");
    }

    @Test
    void noNoteWhenTheUnitsMatch() {
        save("CMPT", "225", "Data Structures and Programming", "3.00");

        assertThat(courseOptions.unitsNote("cmpt", "225", new BigDecimal("3"))).isEmpty();
    }

    // Courses the app doesn't know, like most breadth courses or transfer credit, can't be checked
    @Test
    void noNoteForACourseTheAppDoesntKnow() {
        assertThat(courseOptions.unitsNote("PSYC", "100", new BigDecimal("3.00"))).isEmpty();
    }

    @Test
    void oneUnitIsSingular() {
        save("ENSC", "120", "Electronics Lab", "1.00");

        assertThat(courseOptions.unitsNote("ENSC", "120", new BigDecimal("2.00")))
                .contains("SFU currently lists ENSC 120 as 1 unit. Check your transcript if yours is different.");
    }
}
