package cmpt276.group16.requisites;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import cmpt276.group16.models.Course;
import cmpt276.group16.requisites.Requirement.And;
import cmpt276.group16.requisites.Requirement.CourseReq;
import cmpt276.group16.requisites.Requirement.MinUnits;
import cmpt276.group16.requisites.Requirement.Or;
import cmpt276.group16.requisites.Requirement.Special;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Prerequisites entered by hand, for courses whose Calendar text the parser can't (or shouldn't) read.
// They're kept in src/main/resources/requisites/manual-rules.json, one entry per course:
//   { "course": "CMPT 276", "reason": "why it's entered by hand", "prerequisite": <node> }
// where a node is one of
//   { "and": [<node>, ...] }   { "or": [<node>, ...] }
//   { "course": "CMPT 225", "minGrade": "C-", "concurrentOk": true }   (minGrade and concurrentOk are optional)
//   { "units": 60 }            { "special": "One W course" }
// The file is read once at startup, and a mistake in it stops the app instead of saving a wrong rule
@Component
public class ManualRules {

    private final Map<String, Requirement> byCourse;

    @Autowired
    public ManualRules(@Value("${app.manual-rules:classpath:requisites/manual-rules.json}") Resource file) {
        try (InputStream in = file.getInputStream()) {
            this.byCourse = read(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't read " + file, e);
        }
    }

    // For tests: the same format as the file, from a string
    static ManualRules fromJson(String json) {
        return new ManualRules(read(new ObjectMapper().readTree(json)));
    }

    private ManualRules(Map<String, Requirement> byCourse) {
        this.byCourse = byCourse;
    }

    // The hand-entered prerequisite for this course, if there is one
    public Optional<Requirement> forCourse(Course course) {
        return Optional.ofNullable(byCourse.get(course.getSubject() + " " + course.getNumber()));
    }

    private static Map<String, Requirement> read(JsonNode root) {
        Map<String, Requirement> rules = new HashMap<>();
        for (JsonNode entry : root.get("rules")) {
            try {
                String course = code(entry.path("course").asString());
                if (rules.put(course, node(entry.get("prerequisite"))) != null) {
                    throw new IllegalArgumentException("listed more than once");
                }
            } catch (IllegalArgumentException e) {
                // Also where a Requirement turns down a node, for example a grade SFU doesn't have
                throw new IllegalStateException("Manual rule for " + entry.path("course") + ": " + e.getMessage(), e);
            }
        }
        return rules;
    }

    private static Requirement node(JsonNode node) {
        if (node == null) {
            throw new IllegalArgumentException("missing a prerequisite");
        }
        if (node.has("and")) {
            return new And(nodes(node.get("and")));
        }
        if (node.has("or")) {
            return new Or(nodes(node.get("or")));
        }
        if (node.has("course")) {
            String[] code = code(node.get("course").asString()).split(" ");
            String minGrade = node.has("minGrade") ? node.get("minGrade").asString() : null;
            boolean concurrentOk = node.has("concurrentOk") && node.get("concurrentOk").asBoolean();
            return new CourseReq(code[0], code[1], minGrade, concurrentOk);
        }
        if (node.has("units")) {
            return new MinUnits(node.get("units").decimalValue());
        }
        if (node.has("special")) {
            return new Special(node.get("special").asString());
        }
        throw new IllegalArgumentException("not a rule node: " + node);
    }

    private static List<Requirement> nodes(JsonNode array) {
        List<Requirement> parts = new ArrayList<>();
        array.forEach(child -> parts.add(node(child)));
        return parts;
    }

    // "cmpt 276" -> "CMPT 276", stored the same way as Course
    private static String code(String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("not a course code: " + text);
        }
        return Course.normalizeSubjectOrNumber(parts[0]) + " " + Course.normalizeSubjectOrNumber(parts[1]);
    }
}
