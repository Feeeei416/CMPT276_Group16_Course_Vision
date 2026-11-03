package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// Plain unit tests: no Spring or database, just the rules
class PasswordRulesTest {

    // Exactly 8 characters is allowed, and so are spaces
    @ParameterizedTest
    @ValueSource(strings = {"12345678", "pass word with spaces"})
    void acceptsPasswordsWithinTheLimits(String password) {
        assertThat(PasswordRules.problems(password)).isEmpty();
    }

    @Test
    void acceptsExactly72Bytes() {
        assertThat(PasswordRules.problems("a".repeat(72))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "1234567"})
    void rejectsPasswordsUnder8Characters(String password) {
        assertThat(PasswordRules.problems(password)).containsExactly("Password must be at least 8 characters.");
    }

    @Test
    void rejectsPasswordsOver72Bytes() {
        assertThat(PasswordRules.problems("a".repeat(73)))
            .containsExactly("Password is too long (at most 72 characters, fewer if it uses accented or non-English letters).");
    }

    // "é" takes 2 bytes, so 37 of them is only 37 characters but 74 bytes
    @Test
    void countsBytesNotCharacters() {
        assertThat(PasswordRules.problems("é".repeat(37))).hasSize(1);
    }
}
