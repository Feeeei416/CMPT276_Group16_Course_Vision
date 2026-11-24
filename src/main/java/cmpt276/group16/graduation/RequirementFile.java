package cmpt276.group16.graduation;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import cmpt276.group16.models.Designation;
import cmpt276.group16.models.Grades;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementKind;
import cmpt276.group16.services.PlanRules;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// The graduation requirements in src/main/resources/requirements/requirements.json, read and checked once at startup.
// docs/graduation-requirements.md says where each rule comes from. The file looks like:
//   { "sets": [ { "program": "COMPUTING_SCIENCE", "entryYear": 2024, "calendarUrl": "https://...",
//                 "version": 2, "requirements": [ <requirement>, ... ] } ] }
// where every requirement has a "category" and a "kind", plus the fields its kind uses:
//   COURSE       "courses": ["CMPT 120", "CMPT 125"]          one requirement for each course
//   CHOOSE       "count": 1, "courses": [...], "title"
//   UNITS_FROM   "units": 12, "courses": [...] and/or "subject": "CMPT" + "minNumber": 400,
//                "excluded": [...], "maxCoursesBelow": 2 + "belowNumber": 400, "title"
//   AREAS        "count": 5, "areas": { "Artificial Intelligence": [...], ... }, "required": ["CMPT 307"]
//                (required courses in the same area are alternatives, like CMPT 300 or 303)
//   DESIGNATION  "units": 6, "designations": ["W"] or ["B_HUM", "B_SOC"], "outsideSubject": "CMPT" to leave out
//                courses in the major subject
//   TOTAL_UNITS  "units": 120, "minNumber": 300 for upper division only
//   NOTE         "text": "..."
// Any requirement can also have "minGrade": "C-" and "exclusive": true.
// A mistake in the file stops the app and says which set and requirement it's in, instead of saving a wrong rule
@Component
public class RequirementFile {

    // One program and entry year, as read from the file
    public record SetEntry(Program program, int entryYear, String calendarUrl, int version, List<RuleEntry> requirements) {

        // For example "Computing Science 2024", the way error messages name a set
        public String name() {
            return program.getShowName() + " " + entryYear;
        }
    }

    // One requirement, with only the fields its kind uses filled in
    public record RuleEntry(String category, RequirementKind kind, String title, Integer count, BigDecimal units,
                            String subject, Integer minNumber, Integer maxCoursesBelow, Integer belowNumber,
                            Set<Designation> designations, String outsideSubject, String minGrade, boolean exclusive, String text,
                            List<CourseEntry> courses) {
    }

    // A course a requirement names, with its area for AREAS
    public record CourseEntry(String subject, String number, RequirementCourse.Use use, String area) {

        String code() {
            return subject + " " + number;
        }
    }

    // Fields every requirement can have
    private static final Set<String> COMMON_FIELDS = Set.of("category", "kind", "minGrade", "exclusive");

    // The other fields each kind can have, so a typo like "unit" is caught instead of ignored
    private static final Map<RequirementKind, Set<String>> KIND_FIELDS = Map.of(
            RequirementKind.COURSE, Set.of("courses"),
            RequirementKind.CHOOSE, Set.of("title", "count", "courses"),
            RequirementKind.UNITS_FROM, Set.of("title", "units", "courses", "subject", "minNumber", "excluded",
                    "maxCoursesBelow", "belowNumber"),
            RequirementKind.AREAS, Set.of("title", "count", "areas", "required"),
            RequirementKind.DESIGNATION, Set.of("title", "units", "designations", "outsideSubject"),
            RequirementKind.TOTAL_UNITS, Set.of("title", "units", "minNumber"),
            RequirementKind.NOTE, Set.of("text"));

    private final List<SetEntry> sets;

    @Autowired
    public RequirementFile(
            @Value("${app.graduation-requirements:classpath:requirements/requirements.json}") Resource file) {
        try (InputStream in = file.getInputStream()) {
            this.sets = read(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't read " + file, e);
        }
    }

    // For tests: the same format as the file, from a string
    static RequirementFile fromJson(String json) {
        return new RequirementFile(read(new ObjectMapper().readTree(json)));
    }

    private RequirementFile(List<SetEntry> sets) {
        this.sets = sets;
    }

    // Every set in the file, in file order
    public List<SetEntry> sets() {
        return sets;
    }

    private static List<SetEntry> read(JsonNode root) {
        JsonNode setNodes = root.get("sets");
        if (setNodes == null || !setNodes.isArray()) {
            throw new IllegalStateException("Graduation requirements: the file needs a \"sets\" list");
        }
        List<SetEntry> sets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int setNumber = 0;
        for (JsonNode setNode : setNodes) {
            setNumber++;
            SetEntry set = set(setNode, setNumber);
            if (!seen.add(set.name())) {
                throw new IllegalStateException("Graduation requirements, " + set.name() + ": listed more than once");
            }
            sets.add(set);
        }
        return List.copyOf(sets);
    }

    private static SetEntry set(JsonNode node, int setNumber) {
        Program program;
        int entryYear;
        try {
            program = program(text(node, "program"));
            entryYear = wholeNumber(node, "entryYear");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Graduation requirements, set " + setNumber + ": " + e.getMessage(), e);
        }
        String name = program.getShowName() + " " + entryYear;
        JsonNode ruleNodes = node.get("requirements");
        if (ruleNodes == null || !ruleNodes.isArray() || ruleNodes.isEmpty()) {
            throw new IllegalStateException("Graduation requirements, " + name + ": needs a \"requirements\" list");
        }
        List<RuleEntry> rules = new ArrayList<>();
        int ruleNumber = 0;
        for (JsonNode ruleNode : ruleNodes) {
            ruleNumber++;
            try {
                rules.addAll(rules(ruleNode));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Graduation requirements, " + name + ", requirement " + ruleNumber
                        + describe(ruleNode) + ": " + e.getMessage(), e);
            }
        }
        String calendarUrl = node.has("calendarUrl") ? node.get("calendarUrl").asString() : null;
        // Starts at 1. Raised when a set is fixed, so the loader replaces the saved copy
        int version = 1;
        if (node.has("version")) {
            try {
                version = positive(node, "version");
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Graduation requirements, " + name + ": " + e.getMessage(), e);
            }
        }
        return new SetEntry(program, entryYear, calendarUrl, version, List.copyOf(rules));
    }

    // " (Breadth, AREAS)", so the error points at the right line of the file
    private static String describe(JsonNode node) {
        return " (" + textOr(node, "category", "no category") + ", " + textOr(node, "kind", "no kind") + ")";
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.asString() : fallback;
    }

    // Usually one requirement, but a COURSE entry with several courses becomes one requirement per course
    private static List<RuleEntry> rules(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("not a requirement: " + node);
        }
        String category = text(node, "category");
        RequirementKind kind = kind(text(node, "kind"));
        for (String field : node.propertyNames()) {
            if (!COMMON_FIELDS.contains(field) && !KIND_FIELDS.get(kind).contains(field)) {
                throw new IllegalArgumentException(kind + " doesn't use \"" + field + "\"");
            }
        }
        String minGrade = node.has("minGrade") ? grade(node.get("minGrade").asString()) : null;
        boolean exclusive = node.has("exclusive") && booleanField(node, "exclusive");
        String title = node.has("title") ? text(node, "title") : null;

        return switch (kind) {
            case COURSE -> codes(node, "courses", true).stream()
                    .map(course -> new RuleEntry(category, kind, null, null, null, null, null, null, null,
                            Set.of(), null, minGrade, exclusive, null, List.of(course)))
                    .toList();
            case CHOOSE -> {
                List<CourseEntry> courses = codes(node, "courses", true);
                int count = positive(node, "count");
                if (count > courses.size()) {
                    throw new IllegalArgumentException("count is " + count + " but only " + courses.size()
                            + " courses are listed");
                }
                yield List.of(new RuleEntry(category, kind, title, count, null, null, null, null, null,
                        Set.of(), null, minGrade, exclusive, null, courses));
            }
            case UNITS_FROM -> List.of(unitsFrom(node, category, title, minGrade, exclusive));
            case AREAS -> List.of(areas(node, category, title, minGrade, exclusive));
            case DESIGNATION -> List.of(new RuleEntry(category, kind, title, null, units(node), null, null, null,
                    null, designations(node), node.has("outsideSubject") ? subject(text(node, "outsideSubject")) : null,
                    minGrade, exclusive, null, List.of()));
            case TOTAL_UNITS -> List.of(new RuleEntry(category, kind, title, null, units(node), null,
                    node.has("minNumber") ? courseNumber(node, "minNumber") : null, null, null,
                    Set.of(), null, minGrade, exclusive, null, List.of()));
            case NOTE -> List.of(new RuleEntry(category, kind, null, null, null, null, null, null, null,
                    Set.of(), null, minGrade, exclusive, text(node, "text"), List.of()));
        };
    }

    private static RuleEntry unitsFrom(JsonNode node, String category, String title, String minGrade,
                                       boolean exclusive) {
        BigDecimal units = units(node);
        // A range needs both halves, CMPT and 400 in "CMPT 400 and above"
        if (node.has("subject") != node.has("minNumber")) {
            throw new IllegalArgumentException("\"subject\" and \"minNumber\" go together");
        }
        if (node.has("maxCoursesBelow") != node.has("belowNumber")) {
            throw new IllegalArgumentException("\"maxCoursesBelow\" and \"belowNumber\" go together");
        }
        List<CourseEntry> courses = new ArrayList<>(codes(node, "courses", false));
        String subject = null;
        Integer minNumber = null;
        if (node.has("subject")) {
            subject = subject(text(node, "subject"));
            minNumber = courseNumber(node, "minNumber");
        }
        if (courses.isEmpty() && subject == null) {
            throw new IllegalArgumentException("needs \"courses\", or a \"subject\" and \"minNumber\"");
        }
        for (CourseEntry excluded : codes(node, "excluded", false)) {
            courses.add(new CourseEntry(excluded.subject(), excluded.number(), RequirementCourse.Use.EXCLUDED, null));
        }
        Integer maxCoursesBelow = null;
        Integer belowNumber = null;
        if (node.has("maxCoursesBelow")) {
            maxCoursesBelow = wholeNumber(node, "maxCoursesBelow");
            if (maxCoursesBelow < 0) {
                throw new IllegalArgumentException("\"maxCoursesBelow\" can't be negative");
            }
            belowNumber = courseNumber(node, "belowNumber");
        }
        return new RuleEntry(category, RequirementKind.UNITS_FROM, title, null, units, subject, minNumber,
                maxCoursesBelow, belowNumber, Set.of(), null, minGrade, exclusive, null, noRepeats(courses));
    }

    private static RuleEntry areas(JsonNode node, String category, String title, String minGrade, boolean exclusive) {
        JsonNode areaNodes = node.get("areas");
        if (areaNodes == null || !areaNodes.isObject() || areaNodes.isEmpty()) {
            throw new IllegalArgumentException("needs \"areas\", each with a list of courses");
        }
        int count = positive(node, "count");
        if (count > areaNodes.size()) {
            throw new IllegalArgumentException("count is " + count + " but only " + areaNodes.size()
                    + " areas are listed");
        }
        Set<String> required = new HashSet<>();
        for (CourseEntry course : codes(node, "required", false)) {
            required.add(course.code());
        }
        Map<String, CourseEntry> courses = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> area : areaNodes.properties()) {
            if (!area.getValue().isArray() || area.getValue().isEmpty()) {
                throw new IllegalArgumentException("area \"" + area.getKey() + "\" needs a list of courses");
            }
            for (JsonNode codeNode : area.getValue()) {
                PlanRules.CourseCode code = code(codeNode);
                String key = code.subject() + " " + code.number();
                RequirementCourse.Use use = required.contains(key)
                        ? RequirementCourse.Use.REQUIRED : RequirementCourse.Use.LISTED;
                if (courses.put(key, new CourseEntry(code.subject(), code.number(), use, area.getKey())) != null) {
                    throw new IllegalArgumentException(key + " is listed more than once");
                }
            }
        }
        for (String course : required) {
            if (!courses.containsKey(course)) {
                throw new IllegalArgumentException("required course " + course + " isn't in any area");
            }
        }
        return new RuleEntry(category, RequirementKind.AREAS, title, count, null, null, null, null, null,
                Set.of(), null, minGrade, exclusive, null, List.copyOf(courses.values()));
    }

    // A list of course codes as LISTED courses. Empty when the field is missing and isn't needed
    private static List<CourseEntry> codes(JsonNode node, String field, boolean needed) {
        JsonNode list = node.get(field);
        if (list == null) {
            if (needed) {
                throw new IllegalArgumentException("needs \"" + field + "\"");
            }
            return List.of();
        }
        if (!list.isArray() || list.isEmpty()) {
            throw new IllegalArgumentException("\"" + field + "\" needs to be a list of course codes");
        }
        List<CourseEntry> courses = new ArrayList<>();
        for (JsonNode codeNode : list) {
            PlanRules.CourseCode code = code(codeNode);
            courses.add(new CourseEntry(code.subject(), code.number(), RequirementCourse.Use.LISTED, null));
        }
        return noRepeats(courses);
    }

    private static List<CourseEntry> noRepeats(List<CourseEntry> courses) {
        Set<String> seen = new HashSet<>();
        for (CourseEntry course : courses) {
            if (!seen.add(course.code())) {
                throw new IllegalArgumentException(course.code() + " is listed more than once");
            }
        }
        return List.copyOf(courses);
    }

    private static PlanRules.CourseCode code(JsonNode node) {
        if (!node.isString()) {
            throw new IllegalArgumentException("not a course code: " + node);
        }
        return PlanRules.parseCourseCode(node.asString())
                .orElseThrow(() -> new IllegalArgumentException("not a course code: " + node.asString()));
    }

    private static Set<Designation> designations(JsonNode node) {
        JsonNode list = node.get("designations");
        if (list == null || !list.isArray() || list.isEmpty()) {
            throw new IllegalArgumentException("needs \"designations\", for example [\"W\"]");
        }
        Set<Designation> designations = EnumSet.noneOf(Designation.class);
        for (JsonNode name : list) {
            // "B-Hum" and "B_HUM" both work
            String text = name.asString().trim().toUpperCase(Locale.ROOT).replace('-', '_');
            try {
                designations.add(Designation.valueOf(text));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("not a designation: " + name.asString());
            }
        }
        return designations;
    }

    private static Program program(String text) {
        try {
            return Program.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("not a program: " + text);
        }
    }

    private static RequirementKind kind(String text) {
        try {
            return RequirementKind.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("not a kind of requirement: " + text);
        }
    }

    private static String grade(String grade) {
        if (!Grades.LETTER.contains(grade)) {
            throw new IllegalArgumentException("not a letter grade: " + grade);
        }
        return grade;
    }

    // "cmpt" -> "CMPT"
    private static String subject(String text) {
        if (!text.matches("[A-Za-z]{2,4}")) {
            throw new IllegalArgumentException("not a subject: " + text);
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private static BigDecimal units(JsonNode node) {
        JsonNode value = node.get("units");
        if (value == null || !value.isNumber() || value.decimalValue().signum() <= 0) {
            throw new IllegalArgumentException("needs \"units\" above 0");
        }
        return value.decimalValue();
    }

    private static int positive(JsonNode node, String field) {
        int value = wholeNumber(node, field);
        if (value < 1) {
            throw new IllegalArgumentException("\"" + field + "\" needs to be 1 or more");
        }
        return value;
    }

    // A course level like 300 or 400
    private static int courseNumber(JsonNode node, String field) {
        int value = wholeNumber(node, field);
        if (value < 100 || value > 999) {
            throw new IllegalArgumentException("\"" + field + "\" needs to be a course number like 300");
        }
        return value;
    }

    private static int wholeNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException("needs \"" + field + "\" as a whole number");
        }
        return value.intValue();
    }

    private static boolean booleanField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (!value.isBoolean()) {
            throw new IllegalArgumentException("\"" + field + "\" needs to be true or false");
        }
        return value.booleanValue();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException("needs \"" + field + "\"");
        }
        return value.asString().trim();
    }
}
