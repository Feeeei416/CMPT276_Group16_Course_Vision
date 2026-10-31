package cmpt276.group16.transcript;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

// Reads the courses out of a provided SFU unofficial transcript
@Service
public class TranscriptParser {
	
	// A term heading like "2025 Fall". Courses below it belong to that term
	private static final Pattern TERM = Pattern.compile("^\\d{4} (Spring|Summer|Fall)$");
	
	// A course row with the columns in the order SFU prints them in:
	// subject, number, title, units attempted, units completed, grade (which will be blank while the term is in progress), ...
	// ... grade points, class average, and class enrollment
	// e.g. "ENSC 251 SW Design & Analysis for Engs 4.00 4.00 B+ 13.32 C+ 83"
	// TODO: handle the Repeated column once I get ahold of transcript that uses it
	private static final Pattern COURSE = Pattern.compile(
														  "^([A-Z]{2,4}) (\\d{3}[A-Z]?) (.+?) (\\d+\\.\\d{2}) (\\d+\\.\\d{2})(?: ([A-Z]{1,2}[+-]?))?"
														  + " \\d+\\.\\d{2} \\S+ \\d+$");

	// Starts like a course row, used to catch rows that COURSE missed
	private static final Pattern LOOKS_LIKE_COURSE = Pattern.compile("^[A-Z]{2,4} \\d{3}[A-Z]?\\b.*");

	// Package-private for now
	TranscriptParseResult parseText(String text) {
		List<ParsedCourse> courses = new ArrayList<>();
        List<String> skippedLines = new ArrayList<>();
        String term = null;
		
        for (String rawLine : text.split("\\R")) {
            // PDFBox sometimes puts extra spaces between words, for example "ECON  103"
            String line = rawLine.trim().replaceAll("\\s+", " ");

            if (TERM.matcher(line).matches()) {
                term = line;
                continue;
            }
			
            Matcher course = COURSE.matcher(line);
            if (course.matches() && term != null) {
                courses.add(toCourse(term, course));
            } else if (LOOKS_LIKE_COURSE.matcher(line).matches()) {
                skippedLines.add(line);
            }
        }
        return new TranscriptParseResult(courses, skippedLines);
    }

	private static ParsedCourse toCourse(String term, Matcher row) {
		BigDecimal attempted = new BigDecimal(row.group(4));
        BigDecimal completed = new BigDecimal(row.group(5));
		// Co-op terms show 0.00 attempted and 3.00 completed, and an in progress course shows the opposite
		// so the larger of the two should be the course's unit value
		return new ParsedCourse(term, row.group(1), row.group(2), row.group(3), attempted.max(completed), row.group(6));
	}
}
