package cmpt276.group16.transcript;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

// PDFs for the tests which are built from the text below, it is done this way to be obvious that no student
// data is public
public final class TestTranscripts {

    // fake SFU unofficial transcript: 8 courses over 3 terms, including a co-op course and in-progress courses
    public static final List<String> SAMPLE_LINES = List.of(
            "Unofficial Transcript",
            "Student Name: Pork, John",
            "ID Number: 000000000",
            "Birthdate: Jan 01",
            "Beginning of Undergraduate Record",
            "2025 Fall",
            "Program Computing Science Major",
            "Course Description Repeated Units Attempted Units Completed Grade Grade Points Class Average Class Enrollment",
            "CMPT 120 Intro.Cmpt.Sci/Programming I 3.00 3.00 A- 11.01 B 310",
            "MACM 101 Discrete Math I 3.00 3.00 A 12.00 B- 352",
            "MATH 151 Calculus I 3.00 3.00 B+ 9.99 C+ 418",
            "Term Totals: 9.00 9.00 33.00",
            "Term GPA: 3.67 Cumulative GPA: 3.67",
            "2026 Spring",
            "Program Computing Science Major",
            "CMPT 125 Intro.Cmpt.Sci/Programming II 3.00 3.00 B 9.00 B- 287",
            "ENSC 195 Industrial Internship I 0.00 3.00 P 0.00 - 52",
            "Term Totals: 3.00 3.00 9.00",
            "Term GPA: 3.00 Cumulative GPA: 3.50",
            "2026 Fall",
            "Program Computing Science Major",
            "CMPT 105W Social Issues&Comm Strategies 3.00 0.00 0.00 - 120",
            "CMPT 225 Data Structures/Programming 3.00 0.00 0.00 - 173",
            "CMPT 419 Special Topics in AI 3.00 0.00 0.00 - 64",
            "Course Topic: ST-Machine Learning",
            "Term Totals: 9.00 0.00 0.00",
            "End of Undergraduate Record");

    public static final int SAMPLE_COURSE_COUNT = 8;

    // small pages so that the sample spans two pages like a real transcript
    private static final int LINES_PER_PAGE = 20;

    private TestTranscripts() {}

    public static byte[] samplePdf() throws IOException {
        return pdfWithLines(SAMPLE_LINES);
    }

    // A real PDF with one empty page
    public static byte[] blankPdf() throws IOException {
        return pdfWithLines(List.of());
    }

    public static byte[] pdfWithLines(List<String> lines) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Always at least one page even with no lines
            for (int start = 0; start == 0 || start < lines.size(); start += LINES_PER_PAGE) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 10);
                    content.setLeading(14);
                    content.newLineAtOffset(50, 740);
                    for (String line : lines.subList(start, Math.min(start + LINES_PER_PAGE, lines.size()))) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    // You can run this locally to get a sample PDF you can upload by hand
    public static void main(String[] args) throws IOException {
        Path file = Path.of("target", "sample-transcript.pdf");
        Files.write(file, samplePdf());
        System.out.println("Wrote " + file.toAbsolutePath());
    }
}
