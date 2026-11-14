package cmpt276.group16.requisites;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

// plain unit tests with no Spring or database
class RequisiteTokenizerTest {

	private static String tokens(String text) {
		return RequisiteTokenizer.tokenize(text).stream()
				.map(RequisiteTokenizer.Token::toString)
				.collect(Collectors.joining(" "));
	}

	// Some courses have a w at the end for writing designation
    @Test
	void splitsCmpt376w() {
		assertThat(tokens("CMPT 105W and (CMPT 275 or CMPT 276), with a minimum grade of C-."))
				.isEqualTo("COURSE(CMPT 105W) AND LPAREN COURSE(CMPT 275) OR COURSE(CMPT 276) RPAREN COMMA "
						+ "GRADE(C-) PERIOD");
	}

    @Test
	void splitsMath152() {
		assertThat(tokens("MATH 150 or MATH 151, with a minimum grade of C-; or MATH 154 or MATH 157, "
				+ "with a minimum grade of B."))
				.isEqualTo("COURSE(MATH 150) OR COURSE(MATH 151) COMMA GRADE(C-) SEMICOLON OR COURSE(MATH 154) "
						+ "OR COURSE(MATH 157) COMMA GRADE(B) PERIOD");
	}

    @Test
    void wordsIgnoreCase() {
        assertThat(tokens("CMPT 125 AND CMPT 127, All With A Minimum Grade Of c-"))
                .isEqualTo("COURSE(CMPT 125) AND COURSE(CMPT 127) COMMA GRADE(C-)");
    }

    // Filler is skipped, but only as whole words: the "all" in "allowed" isn't filler
    @Test
    void skipsFiller() {
        assertThat(tokens("Completion of 80 units, both with a minimum grade of C-"))
                .isEqualTo("UNITS(80) COMMA GRADE(C-)");
        assertThatThrownBy(() -> RequisiteTokenizer.tokenize("CMPT 125 allowed"))
                .isInstanceOf(RequisiteParseException.class);
    }

    // Part of CMPT 295's prerequisite text. Anything the tokenizer doesn't know makes the course FAILED
    @Test
    void unknownTextThrows() {
        assertThatThrownBy(() -> RequisiteTokenizer.tokenize("CMPT 102 for students in an Applied Physics program"))
                .isInstanceOf(RequisiteParseException.class)
                .hasMessageContaining("for students in an Applied Physics program");
    }

    @Test
    void highSchoolCourses() {
        assertThat(tokens("BC Pre-Calculus 12 (or equivalent), Chemistry 12 and BC Principles of Physics 12"))
                .isEqualTo("HIGHSCHOOL COMMA HIGHSCHOOL AND HIGHSCHOOL");
    }

    @Test
    void gradeSfuDoesNotHaveThrows() {
        assertThatThrownBy(() -> RequisiteTokenizer.tokenize("CMPT 125 with a minimum grade of D+."))
                .isInstanceOf(RequisiteParseException.class);
    }
}
