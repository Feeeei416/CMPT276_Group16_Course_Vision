package cmpt276.group16.services;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.CourseRepository;
import tools.jackson.databind.ObjectMapper;

// The courses a course-code box can suggest as the student types (static/js/course-search.js), and the
// "SFU lists this course as 3 units" note on My courses.
// The list goes into the page as JSON, so suggestions appear instantly without asking the server on every key
@Service
public class CourseOptions {

    // One suggestion: "CMPT 225", "Data Structures and Programming", 3 units
    public record Option(String code, String subject, String number, String title, String units) {

        static Option of(Course course) {
            return new Option(course.getSubject() + " " + course.getNumber(), course.getSubject(),
                    course.getNumber(), course.getTitle(), UnitText.number(course.getUnits()));
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Comparator<Course> BY_CODE =
            Comparator.comparing(Course::getSubject).thenComparing(Course::getNumber);

    private final CourseRepository courseRepository;

    public CourseOptions(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    // Every course the app knows, for My courses, where a student records courses from any term
    @Transactional(readOnly = true)
    public List<Option> allCourses() {
        return courseRepository.findAll().stream().sorted(BY_CODE).map(Option::of).toList();
    }

    // Only these offerings, for My schedule, where only next semester's courses can be added
    public static List<Option> of(List<CourseOffering> offerings) {
        return offerings.stream().map(CourseOffering::getCourse).sorted(BY_CODE).map(Option::of).toList();
    }

    // The options as JSON for a <script type="application/json"> block. "</" is written as "<\/" (the same
    // string in JSON), so a course title can never end the script block early
    public static String json(List<Option> options) {
        return JSON.writeValueAsString(options).replace("</", "<\\/");
    }

    // A note when the typed units don't match what SFU lists for the course now, for example
    // "SFU currently lists CMPT 225 as 3 units. Check your transcript if yours is different."
    // Only a note: units change between years (ENSC 204 was 1 unit in 2023 and 3 in 2024), so the transcript wins.
    // Empty when they match, or the course isn't one the app knows
    @Transactional(readOnly = true)
    public Optional<String> unitsNote(String subject, String number, BigDecimal units) {
        return courseRepository.findByNormalizedSubjectAndNumber(subject, number)
                .filter(course -> course.getUnits() != null && course.getUnits().compareTo(units) != 0)
                .map(course -> "SFU currently lists " + course.getSubject() + " " + course.getNumber() + " as "
                        + UnitText.units(course.getUnits()) + ". Check your transcript if yours is different.");
    }
}
