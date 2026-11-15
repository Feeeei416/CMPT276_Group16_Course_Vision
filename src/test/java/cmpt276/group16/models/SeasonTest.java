package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Plain unit tests: the names pages show for each season, and that plan terms use the same ones
class SeasonTest {

    @ParameterizedTest
    @CsvSource({"SPRING, Spring", "SUMMER, Summer", "FALL, Fall"})
    void showsEachSeasonTheWayTranscriptsWriteIt(Season season, String showName) {
        assertThat(season.getShowName()).isEqualTo(showName);
    }

    // Term ordering and the "year x 3 + season" numbering rely on this order
    @Test
    void seasonsAreInTheOrderTheyHappen() {
        assertThat(Season.values()).containsExactly(Season.SPRING, Season.SUMMER, Season.FALL);
    }

    @Test
    void planTermNamesUseTheShowName() {
        assertThat(PlanTerm.nameOf(2027, Season.FALL)).isEqualTo("2027 Fall");
    }
}
