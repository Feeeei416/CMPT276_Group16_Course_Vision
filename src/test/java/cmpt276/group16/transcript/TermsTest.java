package cmpt276.group16.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TermsTest {

    @Test
    void sortsByYearThenSpringSummerFall() {
        List<String> terms = new ArrayList<>(List.of("2026 Spring", "2025 Fall", "2026 Fall", "2025 Spring",
                "2025 Summer"));

        terms.sort(Terms.OLDEST_FIRST);

        assertThat(terms).containsExactly("2025 Spring", "2025 Summer", "2025 Fall", "2026 Spring", "2026 Fall");
    }

    @Test
    void termsInAnotherFormatGoLast() {
        List<String> terms = new ArrayList<>(List.of("Transfer", "2025 Fall"));

        terms.sort(Terms.OLDEST_FIRST);

        assertThat(terms).containsExactly("2025 Fall", "Transfer");
    }

    @Test
    void splitsATermIntoYearAndSeason() {
        assertThat(Terms.of(2025, "Fall")).isEqualTo("2025 Fall");
        assertThat(Terms.yearOf("2025 Fall")).isEqualTo("2025");
        assertThat(Terms.seasonOf("2025 Fall")).isEqualTo("Fall");
        assertThat(Terms.yearOf("Transfer")).isEmpty();
    }
}
