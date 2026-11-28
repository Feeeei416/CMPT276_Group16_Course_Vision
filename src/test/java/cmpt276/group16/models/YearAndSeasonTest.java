package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Plain unit tests: no Spring or database
class YearAndSeasonTest {
    
    // A new term starts on the first day of January, May and September
    @ParameterizedTest 
    @CsvSource ({
        "2027-01-01, 2027, SPRING",
        "2027-04-30, 2027, SPRING",
        "2027-05-01, 2027, SUMMER",
        "2027-08-31, 2027, SUMMER",
        "2027-09-01, 2027, FALL",
        "2027-12-31, 2027, FALL"
    })
    void findsTheTermADateIsIn(LocalDate date, int year, Season season) {
        assertThat(YearAndSeason.of(date)).isEqualTo(new YearAndSeason(year, season));
    }

    // Moving past Fall goes into next year's Spring, and moving back past Spring into last year's Fall
    @ParameterizedTest
    @CsvSource({
        "2027, SPRING,  1, 2027, SUMMER",
        "2027, SUMMER,  1, 2027, FALL",
        "2026, FALL,    1, 2027, SPRING",
        "2027, SPRING, -1, 2026, FALL",
        "2026, FALL,   -6, 2024, FALL",
        "2026, FALL,    7, 2029, SPRING",
        "2026, FALL,    0, 2026, FALL"
    })
    void movesForwardAndBack(int year, Season season, int terms, int newYear, Season newSeason) {
        assertThat(new YearAndSeason(year, season).plus(terms)).isEqualTo(new YearAndSeason(newYear, newSeason));
    }

    @Test
    void nextAndPreviousAreOneTermAway() {
        assertThat(new YearAndSeason(2026, Season.FALL).next()).isEqualTo(new YearAndSeason(2027, Season.SPRING));
        assertThat(new YearAndSeason(2027, Season.SPRING).previous()).isEqualTo(new YearAndSeason(2026, Season.FALL));
    }

    // A later year comes after, even when its season is earlier in the year
    @Test
    void sortsOldestFirst() {
        YearAndSeason fall2026 = new YearAndSeason(2026, Season.FALL);
        YearAndSeason spring2027 = new YearAndSeason(2027, Season.SPRING);
        YearAndSeason summer2027 = new YearAndSeason(2027, Season.SUMMER);

        List<YearAndSeason> terms = new ArrayList<>(List.of(summer2027, fall2026, spring2027));
        Collections.sort(terms);

        assertThat(terms).containsExactly(fall2026, spring2027, summer2027);
        assertThat(spring2027.compareTo(new YearAndSeason(2027, Season.SPRING))).isZero();
    }

    // The same text PlanTerm.nameOf gives today, so pages read the same after switching to this
    @Test
    void nameIsTheYearThenTheSeason() {
        assertThat(new YearAndSeason(2027, Season.SPRING).name()).isEqualTo("2027 Spring");
        assertThat(new YearAndSeason(2026, Season.FALL).name()).isEqualTo("2026 Fall");
    }
}
