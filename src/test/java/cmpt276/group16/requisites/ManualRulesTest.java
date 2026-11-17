package cmpt276.group16.requisites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import cmpt276.group16.models.Course;

// Plain unit tests: no Spring or database
class ManualRulesTest {

    private static Course course(String subject, String number) {
        return new Course(subject, number, "Some Course", new BigDecimal("3.00"), null, null, null);
    }

    private static String ruleFor(String prerequisite) {
        return """
                { "rules": [ { "course": "ENSC 999", "reason": "test", "prerequisite": %s } ] }
                """.formatted(prerequisite);
    }

    @Test
    void readsEveryKindOfNode() {
        ManualRules rules = ManualRules.fromJson(ruleFor("""
                { "or": [
                    { "and": [ { "course": "MATH 260", "minGrade": "C-", "concurrentOk": true }, { "units": 60 } ] },
                    { "course": "CMPT 125" },
                    { "special": "One W course" }
                ] }
                """));

        assertThat(rules.forCourse(course("ENSC", "999"))).hasValueSatisfying(rule -> assertThat(rule).hasToString(
                "OR(AND(MATH 260 (C-, concurrent), 60 units), CMPT 125, SPECIAL(One W course))"));
    }

    @Test
    void courseCodesIgnoreCase() {
        ManualRules rules = ManualRules.fromJson("""
                { "rules": [ { "course": "ensc 405w", "reason": "test", "prerequisite": { "course": "ensc 204" } } ] }
                """);

        assertThat(rules.forCourse(course("ENSC", "405W"))).hasValueSatisfying(rule ->
                assertThat(rule).hasToString("ENSC 204"));
    }

    @Test
    void courseWithoutARuleIsEmpty() {
        assertThat(ManualRules.fromJson(ruleFor("{ \"course\": \"CMPT 125\" }")).forCourse(course("CMPT", "120")))
                .isEmpty();
    }

    // Each mistake stops the app at startup and names the course, instead of saving a wrong rule
    @ParameterizedTest
    @ValueSource(strings = {
            "{ \"course\": \"CMPT 125\", \"minGrade\": \"c-\" }",
            "{ \"course\": \"CMPT125\" }",
            "{ \"courses\": \"CMPT 125\" }",
            "{ \"and\": [ { \"course\": \"CMPT 125\" } ] }",
            "{ \"units\": 0 }"})
    void mistakesNameTheCourse(String prerequisite) {
        assertThatThrownBy(() -> ManualRules.fromJson(ruleFor(prerequisite)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENSC 999");
    }

    @Test
    void courseListedTwiceIsAMistake() {
        assertThatThrownBy(() -> ManualRules.fromJson("""
                { "rules": [
                    { "course": "ENSC 999", "reason": "test", "prerequisite": { "course": "CMPT 125" } },
                    { "course": "ensc 999", "reason": "test", "prerequisite": { "course": "CMPT 135" } }
                ] }
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    void theRealFileLoads() {
        ManualRules rules = new ManualRules(new ClassPathResource("requisites/manual-rules.json"));

        assertThat(rules.forCourse(course("CMPT", "276"))).isPresent();
    }
}
