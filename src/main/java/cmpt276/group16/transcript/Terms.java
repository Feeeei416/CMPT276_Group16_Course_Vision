package cmpt276.group16.transcript;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Terms are stored the way the transcript prints them, for example "2025 Fall"
public final class Terms {

    // In the order they happen within a year
    public static final List<String> SEASONS = List.of("Spring", "Summer", "Fall");

    private static final Pattern TERM = Pattern.compile("^(\\d{4}) (Spring|Summer|Fall)$");

    // Oldest first. Saving order isn't enough, since a course added by hand can be from an earlier term
    // Anything that isn't a term goes last
    public static final Comparator<String> OLDEST_FIRST = Comparator.comparingInt(Terms::sortKey);

    private Terms() {
    }

    public static String of(int year, String season) {
        return year + " " + season;
    }

    // "2025" from "2025 Fall", or "" if the term isn't in that format
    public static String yearOf(String term) {
        Matcher m = TERM.matcher(term);
        return m.matches() ? m.group(1) : "";
    }

    // "Fall" from "2025 Fall", or "" if the term isn't in that format
    public static String seasonOf(String term) {
        Matcher m = TERM.matcher(term);
        return m.matches() ? m.group(2) : "";
    }

    private static int sortKey(String term) {
        Matcher m = TERM.matcher(term);
        if (!m.matches()) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(m.group(1)) * SEASONS.size() + SEASONS.indexOf(m.group(2));
    }
}
