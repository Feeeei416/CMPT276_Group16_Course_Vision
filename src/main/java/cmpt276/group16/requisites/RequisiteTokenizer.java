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
        COURSE, AND, OR, ALL, GRADE, COMMA, SEMICOLON, LPAREN, RPAREN, PERIOD
    }

    // text is the course code for COURSE, the letter grade for GRADE, then null for the rest
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
            "(?<AND>(?i:and)\\b)",
            "(?<OR>(?i:or)\\b)",
            "(?<ALL>(?i:all)\\b)",
            "(?<GRADE>(?i:with a minimum grade of) (?<letter>[A-Fa-f][+-]?))",
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
            tokens.add(tokenFrom(m));
            pos = m.end();
        }
        return tokens;
    }
	
    private static Token tokenFrom(Matcher m) {
        if (m.group("COURSE") != null) {
            return new Token(Type.COURSE, m.group("COURSE"));
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
