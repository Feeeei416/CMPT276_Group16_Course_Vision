package cmpt276.group16.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.HtmlUtils;

// Reads the body rows of the course table on a rendered page, so tests can check what's in each row
// instead of only checking that some text appears somewhere on the page
final class CourseTableRows {

    private static final Pattern TABLE = Pattern.compile("<table class=\"course-table\">(.*?)</table>", Pattern.DOTALL);
    private static final Pattern BODY_ROW = Pattern.compile("<tr>\\s*(<td.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    private CourseTableRows() {}

    // One list of cell texts per row: term, subject, number, title, units, grade
    static List<List<String>> in(String html) {
        Matcher table = TABLE.matcher(html);
        if (!table.find()) {
            return List.of();
        }
        List<List<String>> rows = new ArrayList<>();
        Matcher row = BODY_ROW.matcher(table.group(1));
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(HtmlUtils.htmlUnescape(cell.group(1).trim()));
            }
            rows.add(cells);
        }
        return rows;
    }
}
