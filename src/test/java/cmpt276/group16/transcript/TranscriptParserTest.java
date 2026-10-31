package cmpt276.group16.transcript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TranscriptParserTest {

	private final TranscriptParser parser = new TranscriptParser();

	// The lines below are in the format PDFBox extracts from a real SFU transcript

	@Test
	void readsAGradedCourse() {
		List<ParsedCourse> courses = parser.parseText("""
				2024 Fall
				ENSC 251 SW Design & Analysis for Engs 4.00 4.00 B+ 13.32 C+ 83
				""").courses();
		assertThat(courses).containsExactly(new ParsedCourse(
				"2024 Fall", "ENSC", "251", "SW Design & Analysis for Engs", new BigDecimal("4.00"), "B+"));
	}

	// Co-op terms show 0.00 attempted and 3.00 completed
	@Test
	void readsACoopCourseUsingCompletedUnits() {
		ParsedCourse course = parser.parseText("""
				2025 Spring
				ENSC 195 Industrial Internship I 0.00 3.00 P 0.00 - 52
				""").courses().get(0);
		assertThat(course.units()).isEqualByComparingTo("3.00");
		assertThat(course.grade()).isEqualTo("P");
	}

	// In-progress courses have no grade, and "-" is the class average (not the
	// grade)
	@Test
	void readsAnInProgressCourseWithNoGrade() {
		ParsedCourse course = parser.parseText("""
				2026 Fall
				CMPT 276 Intro Software Engineering 3.00 0.00 0.00 - 141
				""").courses().get(0);
		assertThat(course.grade()).isNull();
		assertThat(course.isInProgress()).isTrue();
		assertThat(course.units()).isEqualByComparingTo("3.00");
	}

	@Test
	void readsCourseNumbersWithALetter() {
		ParsedCourse course = parser.parseText("""
				2024 Fall
				CMPT 105W Social Issues&Comm Strategies 3.00 3.00 B+ 9.99 B 120
				""").courses().get(0);
		assertThat(course.number()).isEqualTo("105W");
		assertThat(course.title()).isEqualTo("Social Issues&Comm Strategies");
	}

	@Test
	void eachCourseGetsTheTermAboveIt() {
		List<ParsedCourse> courses = parser.parseText("""
				2024 Summer
				ENSC 252 Fund. Digital Logic & Design 4.00 4.00 B 12.00 C+ 48
				2024 Fall
				MACM 201 Discrete Math II 3.00 3.00 A- 11.01 B- 204
				""").courses();
		assertThat(courses).extracting(ParsedCourse::term).containsExactly("2024 Summer", "2024 Fall");
	}

	// PDFBox puts two spaces in some course codes (e.g. "ECON  103")
	@Test
	void ignoresExtraSpaces() {
		ParsedCourse course = parser.parseText("""
				2023 Fall
				ECON  103 Principles of Microeconomics 4.00 4.00 C+ 9.32 C 315
				""").courses().get(0);
		assertThat(course.subject()).isEqualTo("ECON");
		assertThat(course.number()).isEqualTo("103");
	}

	@Test
	void ignoresTotalsAndOtherNonCourseLines() {
		TranscriptParseResult result = parser.parseText("""
				UNOFFIC
				Student Name: Pork, John
				University of Somewhere Else Total Units: 18.00
				2026 Fall
				ENSC 461 Special Topics Engineering Sci 4.00 0.00 0.00 - 37
				Course Topic: ST-Robotics
				 Term Totals: 4.00 0.00 0.00
				 Cumulative Totals: 4.00 0.00 0.00
				Term GPA: 0.00 Cumulative GPA: 0.00
				Page 1 of 1
				""");
		assertThat(result.courses()).extracting(ParsedCourse::number).containsExactly("461");
		assertThat(result.skippedLines()).isEmpty();
	}

	@Test
	void reportsLinesThatLookLikeCoursesButDontMatch() {
		TranscriptParseResult result = parser.parseText("""
				2024 Fall
				CMPT 225 Data Structures/Programming 3.00 3.00 B- 8.01 C+ 162
				CMPT 307 Data Structures 3.00
				""");
		assertThat(result.courses()).hasSize(1);
		assertThat(result.skippedLines()).containsExactly("CMPT 307 Data Structures 3.00");
	}

	@Test
	void readsEveryCourseFromTheSamplePdf() throws IOException {
		TranscriptParseResult result = parser.parse(TestTranscripts.samplePdf());

		assertThat(result.courses()).extracting(c -> c.subject() + " " + c.number()).containsExactly(
				"CMPT 120", "MACM 101", "MATH 151",
				"CMPT 125", "ENSC 195",
				"CMPT 105W", "CMPT 225", "CMPT 419");
		assertThat(result.courses().get(0)).isEqualTo(new ParsedCourse(
				"2025 Fall", "CMPT", "120", "Intro.Cmpt.Sci/Programming I", new BigDecimal("3.00"), "A-"));
		assertThat(result.courses()).filteredOn(ParsedCourse::isInProgress)
				.extracting(ParsedCourse::term).containsOnly("2026 Fall").hasSize(3);
		assertThat(result.skippedLines()).isEmpty();
	}

	@Test
	void rejectsAFileThatIsNotAPdf() {
		byte[] text = "CMPT 225 Data Structures/Programming 3.00 3.00 B- 8.01 C+ 162".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> parser.parse(text))
				.isInstanceOf(TranscriptParseException.class)
				.hasMessageContaining("isn't a PDF");
	}

	// Starts like a PDF but isn't one, this could be a download that was cut off
	@Test
	void rejectsADamagedPdf() {
		byte[] damaged = "%PDF-1.7 this is not really a pdf".getBytes(StandardCharsets.US_ASCII);

		assertThatThrownBy(() -> parser.parse(damaged))
				.isInstanceOf(TranscriptParseException.class)
				.hasMessageContaining("couldn't read");
	}

	@Test
	void rejectsAPdfWithNoCourses() {
		assertThatThrownBy(() -> parser.parse(TestTranscripts.blankPdf()))
				.isInstanceOf(TranscriptParseException.class)
				.hasMessageContaining("No courses");
	}
}
