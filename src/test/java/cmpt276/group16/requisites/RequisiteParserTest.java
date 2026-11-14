package cmpt276.group16.requisites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Plain unit tests: no Spring or database
class RequisiteParserTest {

    private static String parse(String text) {
        return RequisiteParser.parse(text).orElseThrow().toString();
    }

    @Test
    void parsesCmpt225() {
        assertThat(parse("(MACM 101 and (CMPT 125, CMPT 129 or CMPT 135)) or (ENSC 251 and ENSC 252), "
                + "all with a minimum grade of C-."))
                .isEqualTo("OR(AND(MACM 101 (C-), OR(CMPT 125 (C-), CMPT 129 (C-), CMPT 135 (C-))), "
                        + "AND(ENSC 251 (C-), ENSC 252 (C-)))");
    }

    @Test
    void parsesCmpt376w() {
        assertThat(parse("CMPT 105W and (CMPT 275 or CMPT 276), with a minimum grade of C-."))
                .isEqualTo("AND(CMPT 105W (C-), OR(CMPT 275 (C-), CMPT 276 (C-)))");
    }

    // Each half of the semicolon has its own grade
    @Test
    void parsesMath152() {
        assertThat(parse("MATH 150 or MATH 151, with a minimum grade of C-; or MATH 154 or MATH 157, "
                + "with a minimum grade of B."))
                .isEqualTo("OR(MATH 150 (C-), MATH 151 (C-), MATH 154 (B), MATH 157 (B))");
    }

    @Test
    void oneCourseWithoutAGrade() {
        assertThat(parse("CMPT 125.")).isEqualTo("CMPT 125");
    }

    @Test
    void joiningWordCanRepeat() {
        assertThat(parse("CMPT 125 or CMPT 128 or CMPT 135")).isEqualTo("OR(CMPT 125, CMPT 128, CMPT 135)");
    }

    @Test
    void oxfordComma() {
        assertThat(parse("CMPT 125, CMPT 128, and CMPT 135")).isEqualTo("AND(CMPT 125, CMPT 128, CMPT 135)");
    }

    // The grade written closest to a course wins
    @Test
    void innerGradeWins() {
        assertThat(parse("(CMPT 125 with a minimum grade of B) and MACM 101, with a minimum grade of C-."))
                .isEqualTo("AND(CMPT 125 (B), MACM 101 (C-))");
    }

    @Test
    void unitsAndPermission() {
        assertThat(parse("60 units or permission of the instructor."))
                .isEqualTo("OR(60 units, SPECIAL(permission of the instructor))");
    }

    // The sentence after the prerequisites marks courses that can be taken in the same term
    @Test
    void mayBeTakenConcurrently() {
        assertThat(parse("MATH 232 and MATH 260, with a minimum grade of C-. MATH 260 may be taken concurrently."))
                .isEqualTo("AND(MATH 232 (C-), MATH 260 (C-, concurrent))");
    }

    @Test
    void severalMayBeTakenConcurrently() {
        assertThat(parse("CMPT 125 and MATH 151. CMPT 125 and MATH 151 may be taken concurrently."))
                .isEqualTo("AND(CMPT 125 (concurrent), MATH 151 (concurrent))");
    }

    // CMPT 105W's prerequisite text is ""
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void blankMeansNoPrerequisites(String text) {
        assertThat(RequisiteParser.parse(text)).isEmpty();
    }

    // Each of these fails the course, so the student is shown the text instead of a wrong rule
    @ParameterizedTest
    @ValueSource(strings = {
            "CMPT 125 and CMPT 127 or CMPT 135",
            "CMPT 125, CMPT 127",
            "CMPT 125 or CMPT 127, CMPT 135",
            "(CMPT 125 or CMPT 127",
            "CMPT 125 or CMPT 127)",
            "CMPT 125 or",
            "CMPT 125. CMPT 127.",
            "0 units",
            "BC Math 12 or equivalent is recommended.",
            // MATH 260 isn't one of the prerequisites, so it's really a corequisite
            "MATH 232. MATH 260 may be taken concurrently.",
            "60 units. 60 units may be taken concurrently."})
    void unclearTextFails(String text) {
        assertThatThrownBy(() -> RequisiteParser.parse(text)).isInstanceOf(RequisiteParseException.class);
    }
}
