package cmpt276.group16.requisites;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// The required courses are in src/test/resources/programs, and each one's
// prerequisite text comes from its saved SFU API response in src/test/resources/sfu.
// The lists below are what's left to do. Fixing the parser for a course, or giving it a MANUAL rule,
// means taking it off the list; the test fails until the list matches, so it can't go stale
class ProgramCoverageTest {

    // Prerequisite text the parser can't turn into rules yet
    private static final Set<String> NOT_PARSED_YET = Set.of(
            "CHEM 121", "CMPT 120", "CMPT 210", "CMPT 276", "CMPT 295", "CMPT 307",
            "ENSC 120", "ENSC 151", "ENSC 327", "ENSC 380", "ENSC 405W", "ENSC 429",
            "MACM 101", "MACM 316",
            "MATH 150", "MATH 151", "MATH 154", "MATH 155", "MATH 157", "MATH 232", "MATH 240", "MATH 251",
            "MATH 260", "PHYS 120", "PHYS 121");

    // In the Calendar, but not offered since at least 2022, so the API has no outline to read
    private static final Set<String> NO_OUTLINE = Set.of("ENSC 411");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Path resources(String folder) throws URISyntaxException {
        return Path.of(ProgramCoverageTest.class.getResource("/" + folder).toURI());
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

    // "CMPT 105W" -> sfu/cmpt-105w-d100.json, whichever section was saved
    private static Optional<Path> outlineOf(String course) throws IOException, URISyntaxException {
        String prefix = course.toLowerCase(Locale.ROOT).replace(' ', '-') + "-";
        try (Stream<Path> files = Files.list(resources("sfu"))) {
            return files.filter(f -> {
                String name = f.getFileName().toString();
                return name.startsWith(prefix) && !name.endsWith("-sections.json");
            }).sorted().findFirst();
        }
    }

    private static boolean parses(Path outline) throws IOException {
        String text = JSON.readTree(outline.toFile()).get("info").get("prerequisites").asString();
        try {
            RequisiteParser.parse(text);
            return true;
        } catch (RequisiteParseException e) {
            return false;
        }
    }

    // Every problem at once, so one run shows the whole list instead of the first mismatch
    @Test
    void everyRequiredCourseIsCoveredOrListed() throws IOException, URISyntaxException {
        List<String> problems = new ArrayList<>();
        for (String course : requiredCourses()) {
            Optional<Path> outline = outlineOf(course);
            if (outline.isEmpty()) {
                if (!NO_OUTLINE.contains(course)) {
                    problems.add(course + ": no saved outline in src/test/resources/sfu");
                }
                continue;
            }
            if (NO_OUTLINE.contains(course)) {
                problems.add(course + ": has an outline now, take it off NO_OUTLINE");
            }
            boolean parses = parses(outline.get());
            if (!parses && !NOT_PARSED_YET.contains(course)) {
                problems.add(course + ": doesn't parse");
            }
            if (parses && NOT_PARSED_YET.contains(course)) {
                problems.add(course + ": parses now, take it off NOT_PARSED_YET");
            }
        }
        assertThat(problems).isEmpty();
    }

    // A typo in either list would otherwise hide a course
    @Test
    void listsOnlyNameRequiredCourses() throws IOException, URISyntaxException {
        Set<String> required = requiredCourses();
        assertThat(required).containsAll(NOT_PARSED_YET).containsAll(NO_OUTLINE);
    }
}
