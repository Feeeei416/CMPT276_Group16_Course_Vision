package cmpt276.group16.requisites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import cmpt276.group16.requisites.Requirement.And;
import cmpt276.group16.requisites.Requirement.CourseReq;
import cmpt276.group16.requisites.Requirement.MinUnits;
import cmpt276.group16.requisites.Requirement.Or;
import cmpt276.group16.requisites.Requirement.Special;

// Plain unit tests: no Spring or database
class RequirementTest {

    private static CourseReq course(String subject, String number) {
        return new CourseReq(subject, number, "C-");
    }

    // CMPT 225: "(MACM 101 and (CMPT 125, CMPT 129 or CMPT 135)) or (ENSC 251 and ENSC 252),
    // all with a minimum grade of C-." The parser's tests compare against strings like this one
    @Test
    void printsANestedTreeOnOneLine() {
        Requirement cmpt225 = new Or(List.of(
                new And(List.of(
                        course("MACM", "101"),
                        new Or(List.of(course("CMPT", "125"), course("CMPT", "129"), course("CMPT", "135"))))),
                new And(List.of(course("ENSC", "251"), course("ENSC", "252")))));

        assertThat(cmpt225).hasToString(
                "OR(AND(MACM 101 (C-), OR(CMPT 125 (C-), CMPT 129 (C-), CMPT 135 (C-))), "
                        + "AND(ENSC 251 (C-), ENSC 252 (C-)))");
    }

    @Test
    void printsLeaves() {
        assertThat(new CourseReq("CMPT", "105W", null)).hasToString("CMPT 105W");
        assertThat(new CourseReq("MATH", "260", "C-", true)).hasToString("MATH 260 (C-, concurrent)");
        assertThat(new CourseReq("MATH", "260", null, true)).hasToString("MATH 260 (concurrent)");
        assertThat(new MinUnits(new BigDecimal("60.00"))).hasToString("60 units");
        assertThat(new Special("permission of the instructor")).hasToString("SPECIAL(permission of the instructor)");
    }

    // An empty OR could never be met, so it would block every student
    @Test
    void groupsNeedAtLeastTwoParts() {
        assertThatThrownBy(() -> new Or(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new And(List.of(course("CMPT", "125")))).isInstanceOf(IllegalArgumentException.class);
    }

    // A grade that isn't on SFU's scale could never be met, even by a student with an A+
    @Test
    void minGradeMustBeALetterGrade() {
        assertThatThrownBy(() -> new CourseReq("CMPT", "125", "c-")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourseReq("CMPT", "125", "P")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leavesNeedSomethingToShow() {
        assertThatThrownBy(() -> new MinUnits(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MinUnits(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Special(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
