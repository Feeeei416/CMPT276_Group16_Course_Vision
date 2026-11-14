package cmpt276.group16.requisites;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cmpt276.group16.models.Grades;

// This splits the prerequisite text into tokens, like:
// "CMPT 105W and (CMPT 275 or CMPT 276), with a minimum grade of C-."
// into
// COURSE(CMPT 105W) AND LPAREN COURSE(CMPT 275) OR COURSE(CMPT 276) RPAREN COMMA GRADE(C-) PERIOD
public final class RequisiteTokenizer {

    public enum Type {
        COURSE, NUMBER, UNITS, SPECIAL, AND, OR, ONEOF, INCLUDING, GRADE, CONCURRENT,
        COMMA, SEMICOLON, LPAREN, RPAREN, PERIOD
    }

    // text is the course code for COURSE, the course number for NUMBER, the number for UNITS,
    // the wording for SPECIAL, the letter grade for GRADE, then null for the rest
    public record Token(Type type, String text) {
        @Override
        public String toString() {
            return text == null ? type.name() : type + "(" + text + ")";
        }
    }

    // One named group per token type, tried in order at the current position.
    // Words ignore case, but course codes don't, so "and 100 units" isn't read as course "and 100"
    private static final Pattern TOKEN = Pattern.compile(String.join("|",
            "(?<COURSE>[A-Z]{2,4} \\d{3}[A-Z]?)",
            // Words that don't change what's required, so they're skipped wherever they appear, for example
            // "Completion of 80 units", "all with a minimum grade of", or ENSC 410's
            // "A minimum of 80 units is required to enroll in this course".
            // Only add a phrase here if the prerequisite means the same with it taken out
            "(?<FILLER>(?i:completion of|a minimum of|all|both|is required to enroll in this course)\\b)",
            "(?<UNITS>(?<count>\\d+) (?i:units)\\b)",
            // A course number without its subject, like the 126 in "PHYS 121 or 126". After UNITS, so "100 units" isn't one
            "(?<NUMBER>\\d{3}[A-Z]?\\b)",
            // This is only the wordings currently known so anything else fails the course instead of being guessed at
            "(?<SPECIAL>(?i:permission of the (?:instructor|department)))",
            // Not the "and" in "and/or"
            "(?<AND>(?i:and)\\b(?!/))",
            // "A and/or B" is met by either one, which is what OR already means
            "(?<OR>(?i:and/or|or)\\b)",
            // "one of ENSC 100, ENSC 106, or MSE 102" or "either ENSC 250 or CMPT 250"
            "(?<ONEOF>(?i:one of|either)\\b)",
            "(?<INCLUDING>(?i:including)\\b)",
            "(?<GRADE>(?i:with a minimum grade of) (?<letter>[A-Fa-f][+-]?))",
            // The course after "with" is the one whose prerequisite this is, as in ENSC 280's
            // "MATH 251 and/or MATH 232 may be taken concurrently with ENSC 280."
            "(?<CONCURRENT>(?i:may be taken concurrently)(?: (?i:with) [A-Z]{2,4} \\d{3}[A-Z]?)?\\b)",
            "(?<COMMA>,)",
            "(?<SEMICOLON>;)",
            "(?<LPAREN>\\()",
            "(?<RPAREN>\\))",
            "(?<PERIOD>\\.)"));

    private RequisiteTokenizer() {
    }

    public static List<Token> tokenize(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(text);
        int pos = 0;
        while (pos < text.length()) {
            if (Character.isWhitespace(text.charAt(pos))) {
                pos++;
                continue;
            }
            m.region(pos, text.length());
            if (!m.lookingAt()) {
                throw new RequisiteParseException("Can't read \"" + text.substring(pos) + "\"");
            }
            if (m.group("FILLER") == null) {
                tokens.add(tokenFrom(m));
            }
            pos = m.end();
        }
        return tokens;
    }
	
    private static Token tokenFrom(Matcher m) {
        if (m.group("COURSE") != null) {
            return new Token(Type.COURSE, m.group("COURSE"));
        }
        if (m.group("NUMBER") != null) {
            return new Token(Type.NUMBER, m.group("NUMBER"));
        }
        if (m.group("UNITS") != null) {
            return new Token(Type.UNITS, m.group("count"));
        }
        if (m.group("SPECIAL") != null) {
            return new Token(Type.SPECIAL, m.group("SPECIAL"));
        }
        if (m.group("GRADE") != null) {
            String grade = m.group("letter").toUpperCase();
            // Note that the pattern allows things like "D+", which SFU doesn't have
            if (!Grades.LETTER.contains(grade)) {
                throw new RequisiteParseException("Not a letter grade: " + grade);
            }
            return new Token(Type.GRADE, grade);
        }
        for (Type type : Type.values()) {
            if (m.group(type.name()) != null) {
                return new Token(type, null);
            }
        }
        throw new IllegalStateException("TOKEN matched without a named group");
    }
}
