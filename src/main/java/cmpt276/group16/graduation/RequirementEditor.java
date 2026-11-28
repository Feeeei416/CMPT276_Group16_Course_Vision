package cmpt276.group16.graduation;

import java.math.BigDecimal;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.graduation.RequirementFile.RuleEntry;
import cmpt276.group16.models.GraduationRequirement;
import cmpt276.group16.models.GraduationRequirementRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementCourseRepository;
import cmpt276.group16.models.RequirementSet;
import cmpt276.group16.models.RequirementSetRepository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// What the Graduation requirements page does: shows each requirement written the way requirements.json
// writes it, and adds, changes and removes requirements typed in that format, with the file's checks.
// Any change marks the set as edited in the app, so the loader won't replace it on the next start.
// One transaction per method, and the JSON is checked before anything is saved, so a mistake changes nothing
@Service 
@Transactional 
public class RequirementEditor {

    // The entry years a set can be copied to
    static final int FIRST_ENTRY_YEAR = 2000;
    static final int LAST_ENTRY_YEAR = 2100;

    // One requirement on the page, with its JSON to show
    public record Row(GraduationRequirement requirement, String json) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RequirementSetRepository setRepository;
    private final GraduationRequirementRepository requirementRepository;
    private final RequirementCourseRepository courseRepository;
    private final RequirementLoader loader;

    public RequirementEditor(RequirementSetRepository setRepository,
                             GraduationRequirementRepository requirementRepository,
                             RequirementCourseRepository courseRepository, RequirementLoader loader) {
        this.setRepository = setRepository;
        this.requirementRepository = requirementRepository;
        this.courseRepository = courseRepository;
        this.loader = loader;
    }

    // Every set, for the program and entry year dropdown
    @Transactional(readOnly = true)
    public List<RequirementSet> sets() {
        return setRepository.findAllByOrderByProgramAscEntryYearAsc();
    }

    @Transactional(readOnly = true)
    public Optional<RequirementSet> set(Program program, int entryYear) {
        return setRepository.findByProgramAndEntryYear(program, entryYear);
    }

    // The set to add a requirement to
    @Transactional(readOnly = true)
    public RequirementSet setById(Long setId) {
        return setRepository.findById(setId).orElseThrow();
    }

    // The set's requirements in the order they're shown, each with its JSON
    @Transactional(readOnly = true)
    public List<Row> rows(RequirementSet set) {
        return requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(set).stream()
                .map(requirement -> new Row(requirement,
                        toJson(requirement, courseRepository.findByRequirementOrderByIdAsc(requirement))))
                .toList();
    }

    // One requirement's JSON, to fill the box on the edit page
    @Transactional(readOnly = true)
    public String json(Long requirementId) {
        GraduationRequirement requirement = requirementRepository.findById(requirementId).orElseThrow();
        return toJson(requirement, courseRepository.findByRequirementOrderByIdAsc(requirement));
    }

    // The set the requirement belongs to, so the page can go back to it
    @Transactional(readOnly = true)
    public RequirementSet setOf(Long requirementId) {
        return requirementRepository.findById(requirementId).orElseThrow().getRequirementSet();
    }

    // Adds the requirement at the end of the set.
    // Throws IllegalArgumentException with a message for the page when the JSON has a mistake
    public void add(Long setId, String json) {
        // Checked first, so a mistake saves nothing
        List<RuleEntry> rules = RequirementFile.parseRequirement(json);
        RequirementSet set = setRepository.findById(setId).orElseThrow();
        List<GraduationRequirement> existing = requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(set);
        int position = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getPosition() + 1;
        for (RuleEntry rule : rules) {
            loader.saveRule(set, position++, rule);
        }
        set.markEditedInApp();
    }

    // Replaces the requirement with the one typed, in the same place in the set
    public void update(Long requirementId, String json) {
        // Checked first, so a mistake leaves the requirement as it was
        List<RuleEntry> rules = RequirementFile.parseRequirement(json);
        GraduationRequirement requirement = requirementRepository.findById(requirementId).orElseThrow();
        RequirementSet set = requirement.getRequirementSet();
        int position = requirement.getPosition();
        remove(requirementId);
        for (RuleEntry rule : rules) {
            loader.saveRule(set, position, rule);
        }
        set.markEditedInApp();
    }

    // Removes the requirement and its courses; the rest of the set stays as it was
    public void remove(Long requirementId) {
        GraduationRequirement requirement = requirementRepository.findById(requirementId).orElseThrow();
        RequirementSet set = requirement.getRequirementSet();
        // Courses first, since each one points at the requirement
        courseRepository.deleteAll(courseRepository.findByRequirementOrderByIdAsc(requirement));
        requirementRepository.delete(requirement);
        set.markEditedInApp();
    }

    // A saved requirement written the way requirements.json writes it, so parsing it again gives the same rule.
    // Fields come in the file's order, and empty ones are left out like in the file
    static String toJson(GraduationRequirement requirement, List<RequirementCourse> courses) {
        ObjectNode node = JSON.createObjectNode();
        node.put("category", requirement.getCategory());
        node.put("kind", requirement.getKind().name());
        if (requirement.getTitle() != null) {
            node.put("title", requirement.getTitle());
        }
        switch (requirement.getKind()) {
            case COURSE -> node.set("courses", codes(courses, RequirementCourse.Use.LISTED));
            case CHOOSE -> {
                node.put("count", requirement.getCourseCount());
                node.set("courses", codes(courses, RequirementCourse.Use.LISTED));
            }
            case UNITS_FROM -> {
                node.put("units", plain(requirement.getUnits()));
                if (has(courses, RequirementCourse.Use.LISTED)) {
                    node.set("courses", codes(courses, RequirementCourse.Use.LISTED));
                }
                if (requirement.getSubject() != null) {
                    node.put("subject", requirement.getSubject());
                    node.put("minNumber", requirement.getMinNumber());
                }
                if (has(courses, RequirementCourse.Use.EXCLUDED)) {
                    node.set("excluded", codes(courses, RequirementCourse.Use.EXCLUDED));
                }
                if (requirement.getMaxCoursesBelow() != null) {
                    node.put("maxCoursesBelow", requirement.getMaxCoursesBelow());
                    node.put("belowNumber", requirement.getBelowNumber());
                }
            }
            case AREAS -> {
                node.put("count", requirement.getCourseCount());
                // The courses were saved area by area, so grouping them again keeps the areas in order
                ObjectNode areas = node.putObject("areas");
                for (RequirementCourse course : courses) {
                    ArrayNode area = areas.has(course.getArea())
                            ? (ArrayNode) areas.get(course.getArea()) : areas.putArray(course.getArea());
                    area.add(course.getCode());
                }
                if (has(courses, RequirementCourse.Use.REQUIRED)) {
                    node.set("required", codes(courses, RequirementCourse.Use.REQUIRED));
                }
            }
            case DESIGNATION -> {
                node.put("units", plain(requirement.getUnits()));
                ArrayNode designations = node.putArray("designations");
                requirement.getDesignations().stream().sorted().forEach(d -> designations.add(d.name()));
                if (requirement.getOutsideSubject() != null) {
                    node.put("outsideSubject", requirement.getOutsideSubject());
                }
            }
            case TOTAL_UNITS -> {
                node.put("units", plain(requirement.getUnits()));
                if (requirement.getMinNumber() != null) {
                    node.put("minNumber", requirement.getMinNumber());
                }
            }
            case NOTE -> node.put("text", requirement.getText());
        }
        if (requirement.getMinGrade() != null) {
            node.put("minGrade", requirement.getMinGrade());
        }
        if (requirement.isExclusive()) {
            node.put("exclusive", true);
        }
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }

    // The codes of the courses used this way, for example ["CMPT 307", "CMPT 308"]
    private static ArrayNode codes(List<RequirementCourse> courses, RequirementCourse.Use use) {
        ArrayNode codes = JSON.createArrayNode();
        courses.stream().filter(course -> course.getUse() == use).forEach(course -> codes.add(course.getCode()));
        return codes;
    }

    private static boolean has(List<RequirementCourse> courses, RequirementCourse.Use use) {
        return courses.stream().anyMatch(course -> course.getUse() == use);
    }

    // 12 rather than 12.00, the way the file writes units
    private static BigDecimal plain(BigDecimal units) {
        return new BigDecimal(units.stripTrailingZeros().toPlainString());
    }

    // Copies a set to a new entry year of the same program, as a start for that year's Calendar (#70).
    // Each requirement goes through its JSON, the same way the edit page saves it, so the copy matches exactly.
    // Throws IllegalArgumentException with a message for the page when the year is taken or out of range
    public RequirementSet copy(Long fromSetId, int entryYear) {
        RequirementSet from = setRepository.findById(fromSetId).orElseThrow();
        if (entryYear < FIRST_ENTRY_YEAR || entryYear > LAST_ENTRY_YEAR) {
            throw new IllegalArgumentException(
                    "Choose an entry year from " + FIRST_ENTRY_YEAR + " to " + LAST_ENTRY_YEAR + ".");
        }
        if (setRepository.findByProgramAndEntryYear(from.getProgram(), entryYear).isPresent()) {
            throw new IllegalArgumentException(from.getProgram().getShowName() 
                + " already has requirements for entry " + entryYear + ".");
        }
        
        RequirementSet copy = setRepository.save(new RequirementSet(from.getProgram(), entryYear, from.getCalendarUrl()));
        for (GraduationRequirement requirement : requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(from)) {
            String json = toJson(requirement, courseRepository.findByRequirementOrderByIdAsc(requirement));
            for (RuleEntry rule : RequirementFile.parseRequirement(json)) {
                loader.saveRule(copy, requirement.getPosition(), rule);
            }
        }
        copy.markEditedInApp();
        return copy;
    }
}
