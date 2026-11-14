package cmpt276.group16.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.Season;

// What can go in a plan: which terms can be planned and what a course code looks like
public final class PlanRules {

    // Plans can reach up to the end of the year this many years after next semester
    public static final int MAX_YEARS_AHEAD = 6;

    // A 2 to 4 letter subject and a 3 digit number, with an optional letter like the W in 105W.
    // Spaces between them are optional, so "cmpt225" works too
    private static final Pattern COURSE_CODE = Pattern.compile("^([A-Za-z]{2,4})\\s*(\\d{3}[A-Za-z]?)$");

    // Season lists them in the order they happen within a year
    private static final Season[] SEASONS = Season.values();

    private static final int TERMS_PER_YEAR = SEASONS.length;

    private PlanRules() {}

    public record CourseCode(String subject, String number) {
    }

    // Empty when the text isn't a course code. Saved in capitals, the way SFU writes them.
    // Locale.ROOT so the capitals don't depend on the server's language (in Turkish, "i" becomes "İ")
    public static Optional<CourseCode> parseCourseCode(String text) {
        Matcher m = COURSE_CODE.matcher(text.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new CourseCode(m.group(1).toUpperCase(Locale.ROOT), m.group(2).toUpperCase(Locale.ROOT)));
    }

    // Empty when the text isn't Spring, Summer or Fall. The form sends FALL, but any case works
    public static Optional<Season> parseSeason(String text) {
        try {
            return Optional.of(Season.valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // For example "2027 Spring" in November 2026
    public static String nextSemester(LocalDate today) {
        return termName(currentTerm(today) + 1);
    }

    // The years to offer on the My plan page, from the first plannable term to the last
    public static List<Integer> plannableYears(LocalDate today) {
        int firstYear = (currentTerm(today) + 2) / TERMS_PER_YEAR;
        return IntStream.rangeClosed(firstYear, lastYear(today)).boxed().toList();
    }

    // Returns every rule the term breaks; an empty list means it can be added to a plan
    public static List<String> problemsWithTerm(int year, Season season, LocalDate today) {
        List<String> problems = new ArrayList<>();
        int nextSemester = currentTerm(today) + 1;
        if (termNumber(year, season) <= nextSemester) {
            problems.add("Choose a term after next semester (" + termName(nextSemester) + ").");
        }
        if (year > lastYear(today)) {
            problems.add("Choose a term no later than " + PlanTerm.nameOf(lastYear(today), Season.FALL) + ".");
        }
        return problems;
    }

    // Terms are numbered three a year, so the term after any term is one more
    private static int termNumber(int year, Season season) {
        return year * TERMS_PER_YEAR + season.ordinal();
    }

    private static String termName(int termNumber) {
        return PlanTerm.nameOf(termNumber / TERMS_PER_YEAR, SEASONS[termNumber % TERMS_PER_YEAR]);
    }

    // Spring is January to April, Summer is May to August, and Fall is September to December
    private static int currentTerm(LocalDate today) {
        return termNumber(today.getYear(), SEASONS[(today.getMonthValue() - 1) / 4]);
    }

    private static int lastYear(LocalDate today) {
        return (currentTerm(today) + 1) / TERMS_PER_YEAR + MAX_YEARS_AHEAD;
    }
}
