package cmpt276.group16.requisites;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.ObjectMapper;

// Real prerequisite text, read from the SFU API responses saved in src/test/resources/sfu.
// NONE means no prerequisites, FAILED means the course is flagged and the student is shown the text
class SavedOutlinesParseTest {

    private static String prerequisitesIn(String file) throws IOException {
        try (InputStream in = SavedOutlinesParseTest.class.getResourceAsStream("/sfu/" + file)) {
            return new ObjectMapper().readTree(in).get("info").get("prerequisites").asString();
        }
    }

    private static String parse(String text) {
        try {
            return RequisiteParser.parse(text).map(Requirement::toString).orElse("NONE");
        } catch (RequisiteParseException e) {
            return "FAILED";
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            econ-103-d100.json  | NONE
            ensc-316-d100.json  | AND(MATH 251 (C-), OR(ENSC 220 (C-), MSE 250 (C-)))
            ensc-324-d100.json  | AND(OR(ENSC 220 (C-), MSE 250 (C-)), MATH 232 (C-), OR(MATH 260 (C-), MATH 310 (C-)))
            ensc-350-e100.json  | OR(AND(ENSC 215 (C-), OR(ENSC 250 (C-), CMPT 250 (C-))), AND(ENSC 252 (C-), ENSC 254 (C-)))
            ensc-416-d100.json  | AND(ENSC 316 (C-), MATH 254 (C-))
            ensc-470-e100.json  | AND(80 units, OR(PHYS 121 (C-), PHYS 126 (C-), PHYS 141 (C-)), OR(MATH 260 (C-), MATH 310 (C-)))
            ensc-405w-d100.json | FAILED
            # Not decided yet: "MATH 260 may be taken concurrently." is a second sentence
            ensc-220-d100.json  | FAILED
            """)
    void parsesSavedOutline(String file, String expected) throws IOException {
        assertThat(parse(prerequisitesIn(file))).isEqualTo(expected);
    }
}
