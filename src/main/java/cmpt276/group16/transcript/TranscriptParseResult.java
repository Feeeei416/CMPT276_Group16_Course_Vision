package cmpt276.group16.transcript;

import java.util.List;

// skippedLines are lines that start like a course but dont match, so a course isn't dropped silently
public record TranscriptParseResult(List<ParsedCourse> courses, List<String> skippedLines) {
}
