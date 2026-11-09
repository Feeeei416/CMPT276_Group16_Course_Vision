package cmpt276.group16.transcript;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import cmpt276.group16.models.Grades;
import cmpt276.group16.models.TranscriptCourse;

// A course typed into the Add course or Edit course form, after validate() has passed
// Both forms use the same field names and the same checks
public record CourseInput(String term, String subject, String number, String title, BigDecimal units,
        String grade) {

    // SFU's first term
    public static final int MIN_YEAR = 1965;

    // The units column is precision 4, scale 2
    private static final BigDecimal MAX_UNITS = new BigDecimal("99.99");

    // Same shape as the transcript parser: 2 to 4 capital letters
    private static final Pattern SUBJECT = Pattern.compile("^[A-Z]{2,4}$");

    // "225", "105W", or "1XX" for transfer credit that doesn't match one SFU course
    private static final Pattern NUMBER = Pattern.compile("^\\d[0-9X]{2}[A-Z]?$");

    // The latest year allowed, so a course in progress next term can still be added
    public static int maxYear() {
        return Year.now().getValue() + 1;
    }

    // Returns every problem with the form; an empty list means from(form) is safe to call
    public static List<String> validate(Map<String, String> form) {
        List<String> errors = new ArrayList<>();

        String year = clean(form, "year");
        if (year.isEmpty()) {
            errors.add("Year is required.");
        } else if (parseYear(year) == null) {
            errors.add("Year must be between " + MIN_YEAR + " and " + maxYear() + ".");
        }

        if (!Terms.SEASONS.contains(clean(form, "season"))) {
            errors.add("Choose a term.");
        }

        String subject = upper(form, "subject");
        if (subject.isEmpty()) {
            errors.add("Subject is required.");
        } else if (!SUBJECT.matcher(subject).matches()) {
            errors.add("Subject must be 2 to 4 letters, such as CMPT.");
        }

        String number = upper(form, "number");
        if (number.isEmpty()) {
            errors.add("Course number is required.");
        } else if (!NUMBER.matcher(number).matches()) {
            errors.add("Course number must look like 225, 105W or 1XX.");
        }

        String title = clean(form, "title");
        if (title.isEmpty()) {
            errors.add("Title is required.");
        } else if (title.length() > 255) {
            errors.add("Title must be at most 255 characters.");
        }

        String units = clean(form, "units");
        if (units.isEmpty()) {
            errors.add("Units are required.");
        } else if (parseUnits(units) == null) {
            errors.add("Units must be a number from 0 to 99.99, such as 3 or 3.00.");
        }

        // Empty means the course is in progress
        String grade = upper(form, "grade");
        if (!grade.isEmpty() && !Grades.isValid(grade)) {
            errors.add("Choose a grade from the list.");
        }

        return errors;
    }

    // Only call this after validate(form) returned no errors
    public static CourseInput from(Map<String, String> form) {
        String grade = upper(form, "grade");
        return new CourseInput(
                Terms.of(parseYear(clean(form, "year")), clean(form, "season")),
                upper(form, "subject"),
                upper(form, "number"),
                clean(form, "title"),
                parseUnits(clean(form, "units")),
                grade.isEmpty() ? null : grade);
    }

    // The form values for an existing course, so the Edit form starts filled in
    public static Map<String, String> formFor(TranscriptCourse course) {
        Map<String, String> form = new HashMap<>();
        form.put("year", Terms.yearOf(course.getTerm()));
        form.put("season", Terms.seasonOf(course.getTerm()));
        form.put("subject", course.getSubject());
        form.put("number", course.getNumber());
        form.put("title", course.getTitle());
        form.put("units", course.getUnits().toPlainString());
        form.put("grade", course.getGrade() == null ? "" : course.getGrade());
        return form;
    }

    private static Integer parseYear(String year) {
        try {
            int value = Integer.parseInt(year);
            return value >= MIN_YEAR && value <= maxYear() ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // null unless it's a number from 0 to 99.99 with at most 2 decimal places
    private static BigDecimal parseUnits(String units) {
        try {
            BigDecimal value = new BigDecimal(units);
            if (value.signum() < 0 || value.compareTo(MAX_UNITS) > 0 || value.stripTrailingZeros().scale() > 2) {
                return null;
            }
            // Stored with 2 decimal places, like the transcript prints them
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(Map<String, String> form, String field) {
        String value = form.get(field);
        return value == null ? "" : value.trim();
    }

    private static String upper(Map<String, String> form, String field) {
        return clean(form, field).toUpperCase(Locale.ROOT);
    }
}
