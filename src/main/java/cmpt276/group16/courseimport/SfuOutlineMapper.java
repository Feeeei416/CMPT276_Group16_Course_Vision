package cmpt276.group16.courseimport;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import cmpt276.group16.models.Designation;
import cmpt276.group16.models.SectionMeeting;
import cmpt276.group16.models.SectionType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Turns the JSON text from SfuCourseOutlinesClient into plain Java objects, following what the real API sends
// (see the saved responses in src/test/resources/sfu). 
// It doesn't touch the database: the import decides how these objects become Course and Section rows.
// Values the API sends that we don't know (a new section type, day or designation) are skipped with a log line,
// so one odd value never stops a whole import
@Component 
public class SfuOutlineMapper {
    
    private static final Logger log = LoggerFactory.getLogger(SfuOutlineMapper.class);

    // hours can be one digit, for example "8:30" for MATH 152, which the default "HH:mm" turns down
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    // How the API writes days, for example "Tu" or each part of "Mo, We, Fr"
    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "Mo", DayOfWeek.MONDAY, "Tu", DayOfWeek.TUESDAY, "We", DayOfWeek.WEDNESDAY,
            "Th", DayOfWeek.THURSDAY, "Fr", DayOfWeek.FRIDAY, "Sa", DayOfWeek.SATURDAY, "Su", DayOfWeek.SUNDAY);

    private final ObjectMapper json = new ObjectMapper();

    // One course in a department's list, for example "105W", "Social Issues and Communication Strategies..."
    public record CourseListing(String number, String title) {}

    // One section in a course's list, for example D101, a LAB that goes with group "1"
    public record SectionListing(String code, SectionType type, boolean primary, String associatedClass) {}

    // What one section's outline says about its course, and the section's weekly meetings.
    // Text fields are null when the API sends nothing; units is null if it isn't a plain number
    public record OutlineData(String title, BigDecimal units, String description, String prereqText,
            String coreqText, Set<Designation> designations, List<SectionMeeting> meetings) {}

    // The JSON as the API sends it. Only the fields we use are listed; the rest are ignored.
    @JsonIgnoreProperties (ignoreUnknown = true)
    record RawCourse(String text, String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawSection(String text, String classType, String sectionCode, String associatedClass) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawOutline(RawInfo info, List<RawSchedule> courseSchedule) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawInfo(String title, String units, String description, String prerequisites,
            String corequisites, String designation) {}

    // Named explicitly because "is" at the start of a name can be read as a getter prefix
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawSchedule(String days, String startTime, String endTime, String campus,
            @JsonProperty("isExam") boolean exam) {}

    // Reads a department's course list, the answer to a URL like ...course-outlines?2027/spring/cmpt
    public List<CourseListing> readCourses(String text) {
        List<CourseListing> courses = new ArrayList<>();
        for (RawCourse raw : read(text, RawCourse[].class)) {
            courses.add(new CourseListing(blankToNull(raw.text()), blankToNull(raw.title())));
        }
        return courses;
    }

    // Reads a course's section list, the answer to a URL like ...course-outlines?2027/spring/cmpt/225
    public List<SectionListing> readSections(String text) {
        List<SectionListing> sections = new ArrayList<>();
        for (RawSection raw : read(text, RawSection[].class)) {
            sections.add(new SectionListing(blankToNull(raw.text()), sectionType(raw.sectionCode()),
                    "e".equals(raw.classType()), blankToNull(raw.associatedClass())));
        }
        return sections;
    }
 
    // Reads one section's outline, the answer to a URL like ...course-outlines?2027/spring/cmpt/225/d100
    public OutlineData readOutline(String text) {
        RawOutline raw = read(text, RawOutline.class);
        RawInfo info = raw.info();
        if (info == null) {
            throw new SfuApiException("The outline has no course information", null);
        }
        List<RawSchedule> schedule = raw.courseSchedule() == null ? List.of() : raw.courseSchedule();
        return new OutlineData(blankToNull(info.title()), units(info.units()), blankToNull(info.description()),
                blankToNull(info.prerequisites()), blankToNull(info.corequisites()),
                designations(info.designation()), meetings(schedule));
    }
 
    private <T> T read(String text, Class<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JacksonException e) {
            // The API answered, but not with the JSON it normally sends, so the import can't trust it
            throw new SfuApiException("The SFU Course Outlines API sent JSON that couldn't be read", e);
        }
    }

    // "LEC" -> LEC; types the docs don't list, like "OPL" (open lab), become OTHER
    private static SectionType sectionType(String code) {
        if (code == null) {
            return SectionType.OTHER;
        }
        try {
            return SectionType.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SectionType.OTHER;
        }
    }
 
    // "3" -> 3. Null if the API sends no units or something that isn't a number
    private static BigDecimal units(String units) {
        String value = blankToNull(units);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Units '{}' aren't a number, so they are left empty", units);
            return null;
        }
    }
 
    // "Quantitative/Breadth-Science" -> {Q, B_SCI}. 
    // Names are exactly as the API writes them (all five seen in real outlines); one it doesn't know is skipped with a log line
    private static Set<Designation> designations(String text) {
        Set<Designation> result = EnumSet.noneOf(Designation.class);
        if (blankToNull(text) == null) {
            return result;
        }
        for (String part : text.split("/")) {
            String name = part.trim();
            if (name.equals("Writing")) {
                result.add(Designation.W);
            } else if (name.equals("Quantitative")) {
                result.add(Designation.Q);
            } else if (name.equals("Breadth-Science")) {
                result.add(Designation.B_SCI);
            } else if (name.equals("Breadth-Social Sciences")) {
                result.add(Designation.B_SOC);
            } else if (name.equals("Breadth-Humanities")) {
                result.add(Designation.B_HUM);
            } else if (!name.isEmpty()) {
                log.warn("Unknown designation '{}' skipped", name);
            }
        }
        return result;
    }
 
    // One meeting per day: "Mo, We, Fr" 8:30-9:20 becomes three meetings.
    // Exam rows and rows without a day or times (for example TBA) are left out.
    // Sorted Monday first, then by start time, which is the order Section keeps them in
    private static List<SectionMeeting> meetings(List<RawSchedule> schedule) {
        List<SectionMeeting> result = new ArrayList<>();
        for (RawSchedule row : schedule) {
            if (row.exam()) {
                continue;
            }
            LocalTime start = time(row.startTime());
            LocalTime end = time(row.endTime());
            if (blankToNull(row.days()) == null || start == null || end == null) {
                continue;
            }
            for (String part : row.days().split(",")) {
                DayOfWeek day = DAYS.get(part.trim());
                if (day == null) {
                    log.warn("Unknown day '{}' skipped", part.trim());
                    continue;
                }
                try {
                    result.add(new SectionMeeting(day, start, end, blankToNull(row.campus())));
                } catch (IllegalArgumentException e) {
                    // for example an end time before the start time
                    log.warn("Meeting {} {}-{} skipped: {}", day, start, end, e.getMessage());
                }
            }
        }
        result.sort(Comparator.comparing(SectionMeeting::getDayOfWeek)
                .thenComparing(SectionMeeting::getStartTime));
        return result;
    }
 
    // "8:30" -> 08:30; null if missing or not a time, and the meeting using it is skipped
    private static LocalTime time(String text) {
        String value = blankToNull(text);
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value, TIME);
        } catch (DateTimeParseException e) {
            log.warn("Time '{}' isn't like 8:30 or 14:30, so that meeting is skipped", text);
            return null;
        }
    }
 
    // The API sends "" for "nothing", for example a course without corequisites; we store null instead
    private static String blankToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}
