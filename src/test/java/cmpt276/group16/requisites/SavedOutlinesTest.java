package cmpt276.group16.requisites;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Real prerequisite text, from the SFU API responses saved in src/test/resources/sfu, and what it parses to.
// The table is also the to-do list for the story's acceptance criterion that rules exist for every course the
// Computing Science and Computer Engineering programs require (listed in src/test/resources/programs):
// a FAILED row still needs a parser fix or a MANUAL rule
class SavedOutlinesTest {

    // NONE means no prerequisites. FAILED means the course is flagged and the student is shown the text
    private static final String EXPECTED = """
            chem-121-d100.json  | FAILED
            cmpt-105w-d100.json | NONE
            cmpt-120-d100.json  | FAILED
            cmpt-125-d100.json  | OR(CMPT 120 (C-), CMPT 130 (C-))
            cmpt-201-d100.json  | AND(OR(CMPT 125 (C-), CMPT 135 (C-)), MACM 101 (C-))
            cmpt-210-d100.json  | FAILED
            cmpt-225-d100.json  | OR(AND(MACM 101 (C-), OR(CMPT 125 (C-), CMPT 129 (C-), CMPT 135 (C-))), AND(ENSC 251 (C-), ENSC 252 (C-)))
            cmpt-276-d100.json  | FAILED
            cmpt-295-d100.json  | FAILED
            cmpt-303-d100.json  | AND(CMPT 225 (C-), OR(CMPT 295 (C-), ENSC 254 (C-)), OR(CMPT 201 (C-), ENSC 351 (C-)))
            cmpt-307-d100.json  | FAILED
            cmpt-376w-d200.json | AND(CMPT 105W (C-), OR(CMPT 275 (C-), CMPT 276 (C-)))
            econ-103-d100.json  | NONE
            ensc-100-e100.json  | NONE
            ensc-105w-d100.json | NONE
            ensc-120-d100.json  | FAILED
            ensc-151-d100.json  | FAILED
            ensc-180-d100.json  | AND(ENSC 151 (C-), OR(MATH 151 (C-), MATH 150 (C-)))
            ensc-204-e100.json  | NONE
            ensc-220-d100.json  | AND(OR(PHYS 121 (C-), PHYS 126 (C-), PHYS 141 (C-)), ENSC 120 (C-), MATH 232 (C-), OR(MATH 260 (C-, concurrent), MATH 310 (C-)))
            ensc-225-d100.json  | AND(OR(ENSC 220 (C-), MSE 250 (C-), SEE 230 (C-)), MATH 232 (C-), OR(MATH 260 (C-), MATH 310 (C-)))
            ensc-251-e100.json  | ENSC 151 (C-)
            ensc-252-d100.json  | ENSC 151 (C-)
            ensc-254-e100.json  | AND(ENSC 251 (C-), ENSC 252 (C-))
            ensc-280-d100.json  | AND(OR(AND(PHYS 121 (C-), ENSC 120 (C-)), PHYS 141 (C-)), MATH 251 (C-, concurrent), MATH 232 (C-, concurrent))
            ensc-316-d100.json  | AND(MATH 251 (C-), OR(ENSC 220 (C-), MSE 250 (C-)))
            ensc-320-e100.json  | AND(OR(ENSC 220 (C-), MSE 250 (C-)), MATH 232 (C-), OR(MATH 260 (C-), MATH 310 (C-)))
            ensc-324-d100.json  | AND(OR(ENSC 220 (C-), MSE 250 (C-)), MATH 232 (C-), OR(MATH 260 (C-), MATH 310 (C-)))
            ensc-327-d100.json  | FAILED
            ensc-350-e100.json  | OR(AND(ENSC 215 (C-), OR(ENSC 250 (C-), CMPT 250 (C-))), AND(ENSC 252 (C-), ENSC 254 (C-)))
            ensc-351-e100.json  | AND(OR(AND(ENSC 151 (C-), ENSC 215 (C-), ENSC 250 (C-)), ENSC 254 (C-), AND(CMPT 225 (C-), OR(CMPT 250 (C-), CMPT 295 (C-)))), 60 units)
            ensc-380-d100.json  | FAILED
            ensc-405w-d100.json | FAILED
            ensc-406-d100.json  | AND(100 units, OR(ENSC 100 (C-), ENSC 106 (C-), CMPT 106 (C-), MSE 102 (C-)))
            ensc-410-d100.json  | 80 units
            ensc-416-d100.json  | AND(ENSC 316 (C-), MATH 254 (C-))
            ensc-429-d100.json  | AND(OR(ENSC 327 (C-), ENSC 328 (C-)), OR(ENSC 380 (C-), MSE 280 (C-)), 80 units)
            ensc-440-e100.json  | ENSC 405W (C-)
            ensc-450-d100.json  | AND(OR(ENSC 225 (C-), MSE 251 (C-), SEE 231 (C-)), ENSC 350 (C-), 80 units)
            ensc-452-d100.json  | AND(ENSC 350 (C-), ENSC 351 (C-), 80 units)
            ensc-453-d100.json  | AND(ENSC 350 (C-), ENSC 351 (C-))
            ensc-470-e100.json  | AND(80 units, OR(PHYS 121 (C-), PHYS 126 (C-), PHYS 141 (C-)), OR(MATH 260 (C-), MATH 310 (C-)))
            macm-101-d100.json  | FAILED
            macm-201-d100.json  | OR(MATH 141, MACM 101, AND(ENSC 251, OR(MATH 232, MATH 240)))
            macm-316-d100.json  | FAILED
            math-150-d100.json  | FAILED
            math-151-d100.json  | FAILED
            math-152-d100.json  | OR(MATH 150 (C-), MATH 151 (C-), MATH 154 (B), MATH 157 (B))
            math-154-d100.json  | FAILED
            math-155-d100.json  | FAILED
            math-157-d100.json  | FAILED
            math-158-e200.json  | OR(MATH 150 (C-), MATH 151 (C-), MATH 154 (C-), MATH 157 (C-))
            math-232-d100.json  | FAILED
            math-240-d100.json  | FAILED
            math-251-d100.json  | FAILED
            math-260-d100.json  | FAILED
            phys-120-d100.json  | FAILED
            phys-121-d100.json  | FAILED
            stat-271-d100.json  | CMPT 210 (C-)
            """;

    // In the Calendar, but not offered since at least 2022, so the API has no outline to read
    private static final Set<String> NO_OUTLINE = Set.of("ENSC 411");

    // One section's outline, like "cmpt-105w-d100.json"; leaves out the section lists and other saved responses
    private static final String OUTLINE_FILE = "[a-z]+-\\d{3}[a-z]?-[a-z]\\d{3}\\.json";

    private static final ObjectMapper JSON = new ObjectMapper();

    // File name -> what it should parse to, in table order
    private static Map<String, String> expected() {
        Map<String, String> rows = new LinkedHashMap<>();
        EXPECTED.lines().forEach(line -> {
            String[] columns = line.split("\\|", 2);
            rows.put(columns[0].strip(), columns[1].strip());
        });
        return rows;
    }

    static Stream<Arguments> outlines() {
        return expected().entrySet().stream().map(row -> Arguments.of(row.getKey(), row.getValue()));
    }

    private static Path resources(String folder) throws URISyntaxException {
        return Path.of(SavedOutlinesTest.class.getResource("/" + folder).toURI());
    }

    private static String parse(String file) throws IOException, URISyntaxException {
        String text = JSON.readTree(resources("sfu").resolve(file).toFile()).get("info").get("prerequisites").asString();
        try {
            return RequisiteParser.parse(text).map(Requirement::toString).orElse("NONE");
        } catch (RequisiteParseException e) {
            return "FAILED";
        }
    }

    private static Set<String> requiredCourses() throws IOException, URISyntaxException {
        Set<String> courses = new TreeSet<>();
        try (Stream<Path> files = Files.list(resources("programs"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                for (JsonNode group : JSON.readTree(file.toFile()).get("requirements")) {
                    group.get("courses").forEach(course -> courses.add(course.asString()));
                }
            }
        }
        return courses;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outlines")
    void parsesAsExpected(String file, String expected) throws IOException, URISyntaxException {
        assertThat(parse(file)).isEqualTo(expected);
    }

    // So a newly saved outline can't be left out of the table
    @Test
    void everySavedOutlineIsInTheTable() throws IOException, URISyntaxException {
        try (Stream<Path> files = Files.list(resources("sfu"))) {
            List<String> saved = files.map(f -> f.getFileName().toString()).filter(f -> f.matches(OUTLINE_FILE)).toList();
            assertThat(expected().keySet()).containsExactlyInAnyOrderElementsOf(saved);
        }
    }

    // Every problem at once, so one run shows the whole list instead of the first mismatch
    @Test
    void everyRequiredCourseHasAnOutline() throws IOException, URISyntaxException {
        Set<String> files = expected().keySet();
        List<String> problems = new ArrayList<>();
        for (String course : requiredCourses()) {
            // "CMPT 105W" -> "cmpt-105w-", whichever section was saved
            String prefix = course.toLowerCase(Locale.ROOT).replace(' ', '-') + "-";
            boolean saved = files.stream().anyMatch(file -> file.startsWith(prefix));
            if (!saved && !NO_OUTLINE.contains(course)) {
                problems.add(course + ": no saved outline in src/test/resources/sfu");
            }
            if (saved && NO_OUTLINE.contains(course)) {
                problems.add(course + ": has an outline now, take it off NO_OUTLINE");
            }
        }
        assertThat(problems).isEmpty();
        assertThat(requiredCourses()).containsAll(NO_OUTLINE);
    }
}
