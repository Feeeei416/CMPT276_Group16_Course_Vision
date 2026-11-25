package cmpt276.group16.models;

import java.time.LocalDate;

// A term as a year and a season, for example 2027 and SPRING, whether or not it's saved in terms.
// Spring is January to April, Summer is May to August, and Fall is September to December
public record YearAndSeason(int year, Season season) implements Comparable<YearAndSeason> {

    // Season lists them in the order they happen within a year
    private static final Season[] SEASONS = Season.values();

    private static final int TERMS_PER_YEAR = SEASONS.length;
    
    // The term this date is in, for example 2026 FALL on Nov 24, 2026
    public static YearAndSeason of(LocalDate date) {
        Season season = SEASONS[(date.getMonthValue() - 1) / 4];
        int year = date.getYear();
        return new YearAndSeason(year, season);
    }

    // The term this many terms later, or earlier when terms is negative.
    // For example 2026 FALL plus 1 is 2027 SPRING, and plus -6 is 2024 FALL
    public YearAndSeason plus(int terms) {
        int target = number() + terms;
        int newYear = Math.floorDiv(target, TERMS_PER_YEAR);
        Season newSeason = SEASONS[Math.floorMod(target, TERMS_PER_YEAR)];
        return new YearAndSeason(newYear, newSeason);
    }

    public YearAndSeason next() {
        return plus(1);
    }

    public YearAndSeason previous() {
        return plus(-1);
    }

    // for example "2027 Spring"
    public String name() {
        return year + " " + season.getShowName();
    }

    // Earlier terms come first
    @Override
    public int compareTo(YearAndSeason other) {
        return Integer.compare(number(), other.number());
    }

    // Counts terms from year 0, so terms can be added and compared as plain numbers
    private int number() {
        return year * TERMS_PER_YEAR + season.ordinal();
    }
}
