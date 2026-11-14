package cmpt276.group16.requisites;

import static cmpt276.group16.requisites.RequisiteTokenizer.Type.AND;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.ALL;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.COMMA;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.CONCURRENT;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.COURSE;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.EITHER;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.INCLUDING;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.NUMBER;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.GRADE;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.LPAREN;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.OR;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.PERIOD;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.RPAREN;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.SEMICOLON;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.SPECIAL;
import static cmpt276.group16.requisites.RequisiteTokenizer.Type.UNITS;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import cmpt276.group16.requisites.Requirement.And;
import cmpt276.group16.requisites.Requirement.CourseReq;
import cmpt276.group16.requisites.Requirement.MinUnits;
import cmpt276.group16.requisites.Requirement.Or;
import cmpt276.group16.requisites.Requirement.Special;
import cmpt276.group16.requisites.RequisiteTokenizer.Token;
import cmpt276.group16.requisites.RequisiteTokenizer.Type;

// This turns prerequisite text into a Requirement tree with one method per grammar rule starting with loosest:
//   requisite  := segment ( SEMICOLON OR segment )* PERIOD? concurrent*
//   concurrent := list CONCURRENT PERIOD?
//   segment    := list ( INCLUDING list )? ( COMMA? ALL? GRADE )?
//   list       := item ( separator item )*
//   item       := COURSE | NUMBER | UNITS | SPECIAL | LPAREN segment RPAREN | EITHER item ( OR item )+
// A separator is a comma, "and", "or", or a comma then "and"/"or".
// One list can't mix "and" with "or", so "A, B or C" is OR(A, B, C) but "A and B or C" fails instead of being guessed.
// "either" works like a bracket, so "A and either B or C" is AND(A, OR(B, C))
public final class RequisiteParser {

    private final List<Token> tokens;
    private int pos;
    // The subject of the last course read, for numbers written without one: the 126 in "PHYS 121 or 126"
    private String lastSubject;

    private RequisiteParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // Empty for blank text, which means the course has no prerequisites
    public static Optional<Requirement> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        RequisiteParser parser = new RequisiteParser(RequisiteTokenizer.tokenize(text));
        try {
            Requirement result = parser.requisite();
            if (parser.pos < parser.tokens.size()) {
                throw new RequisiteParseException("Didn't expect " + parser.tokens.get(parser.pos) + " in \"" + text + "\"");
            }
            return Optional.of(result);
        } catch (IllegalArgumentException e) {
            // A Requirement turned down what was read, for example "0 units"
            throw new RequisiteParseException(e.getMessage() + " in \"" + text + "\"");
        }
    }

    // "MATH 150 or MATH 151, with a minimum grade of C-; or MATH 154 or MATH 157, with a minimum grade of B."
    private Requirement requisite() {
        List<Requirement> segments = new ArrayList<>();
        segments.add(segment());
        while (at(0, SEMICOLON)) {
            pos++;
            expect(OR);
            segments.add(segment());
        }
        if (at(0, PERIOD)) {
            pos++;
        }
        Requirement result = group(OR, segments);

        // "MATH 260 may be taken concurrently." after the prerequisites
        while (startsItem(0)) {
            Requirement named = list();
            expect(CONCURRENT);
            if (at(0, PERIOD)) {
                pos++;
            }
            for (CourseReq course : coursesIn(named)) {
                result = concurrently(result, course);
            }
        }
        return result;
    }

    // A list, and the minimum grade for the courses in it that don't have their own
    private Requirement segment() {
        Requirement list = list();
        // "80 units including (PHYS 121 or 126) and MATH 260": the units and everything after "including"
        if (at(0, INCLUDING)) {
            pos++;
            list = group(AND, List.of(list, list()));
        }
        int start = pos;
        if (at(0, COMMA)) {
            pos++;
        }
        if (at(0, ALL)) {
            pos++;
        }
        if (at(0, GRADE)) {
            return withGrade(list, next().text());
        }
        // No grade after all, so the comma must be someone else's
        pos = start;
        return list;
    }

    private Requirement list() {
        List<Requirement> items = new ArrayList<>();
        items.add(item());
        Type joiner = null;
        boolean lastHadWord = false;
        while (true) {
            // Look past the separator first: in "A or B, with a minimum grade of C-" the comma ends the list
            int comma = at(0, COMMA) ? 1 : 0;
            Type word = at(comma, AND) ? AND : at(comma, OR) ? OR : null;
            int length = comma + (word == null ? 0 : 1);
            if (length == 0 || !startsItem(length)) {
                break;
            }
            if (word != null && joiner != null && word != joiner) {
                throw new RequisiteParseException("Mixes \"and\" with \"or\" without brackets");
            }
            if (word != null) {
                joiner = word;
            }
            lastHadWord = word != null;
            pos += length;
            items.add(item());
        }

        if (items.size() == 1) {
            return items.get(0);
        }
        // "A, B" could be either, and "A or B, C" puts the word in the wrong place
        if (joiner == null || !lastHadWord) {
            throw new RequisiteParseException("A list of " + items + " needs \"and\" or \"or\" before its last part");
        }
        return group(joiner, items);
    }

    private Requirement item() {
        Token token = next();
        return switch (token.type()) {
            case COURSE -> {
                String[] code = token.text().split(" ");
                lastSubject = code[0];
                yield new CourseReq(code[0], code[1], null);
            }
            case NUMBER -> {
                if (lastSubject == null) {
                    throw new RequisiteParseException("Course number " + token.text() + " has no subject before it");
                }
                yield new CourseReq(lastSubject, token.text(), null);
            }
            case UNITS -> new MinUnits(new BigDecimal(token.text()));
            case SPECIAL -> new Special(token.text());
            case LPAREN -> {
                Requirement inside = segment();
                expect(RPAREN);
                yield inside;
            }
            // Only "or" can follow, and the group ends with the last option
            case EITHER -> {
                List<Requirement> options = new ArrayList<>();
                options.add(item());
                while (at(0, OR) && startsItem(1)) {
                    pos++;
                    options.add(item());
                }
                if (options.size() < 2) {
                    throw new RequisiteParseException("\"either\" without an \"or\"");
                }
                yield group(OR, options);
            }
            default -> throw new RequisiteParseException("Expected a course but found " + token);
        };
    }

    private boolean startsItem(int offset) {
        return at(offset, COURSE) || at(offset, NUMBER) || at(offset, UNITS) || at(offset, SPECIAL)
                || at(offset, LPAREN) || at(offset, EITHER);
    }

    // True if the token offset places ahead is of this type, without taking it
    private boolean at(int offset, Type type) {
        return pos + offset < tokens.size() && tokens.get(pos + offset).type() == type;
    }

    private Token next() {
        if (pos >= tokens.size()) {
            throw new RequisiteParseException("Ended too early");
        }
        return tokens.get(pos++);
    }

    private void expect(Type type) {
        Token token = next();
        if (token.type() != type) {
            throw new RequisiteParseException("Expected " + type + " but found " + token);
        }
    }

    // A group of one is just that part. "(A or B) or C" is the same as "A or B or C", so it's stored flat
    private static Requirement group(Type joiner, List<Requirement> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        List<Requirement> flat = new ArrayList<>();
        for (Requirement part : parts) {
            if (joiner == AND && part instanceof And and) {
                flat.addAll(and.parts());
            } else if (joiner == OR && part instanceof Or or) {
                flat.addAll(or.parts());
            } else {
                flat.add(part);
            }
        }
        return joiner == AND ? new And(flat) : new Or(flat);
    }

    // Gives every course in the tree this minimum grade, unless a closer one already gave it one
    private static Requirement withGrade(Requirement requirement, String grade) {
        return mapCourses(requirement, course -> course.minGrade() != null
                ? course
                : new CourseReq(course.subject(), course.number(), grade, course.concurrentOk()));
    }

    // Lets the named course be taken in the same term. It has to be one of the prerequisites:
    // a course that isn't would be a corequisite in the wrong place, and guessing is worse than failing
    private static Requirement concurrently(Requirement requirement, CourseReq named) {
        Requirement marked = mapCourses(requirement, course ->
                course.subject().equals(named.subject()) && course.number().equals(named.number())
                        ? new CourseReq(course.subject(), course.number(), course.minGrade(), true)
                        : course);
        // Records compare by value, so nothing changed means the course wasn't found
        if (marked.equals(requirement)) {
            throw new RequisiteParseException(named.subject() + " " + named.number()
                    + " may be taken concurrently, but isn't a prerequisite");
        }
        return marked;
    }

    // The courses in "MATH 232 and MATH 260 may be taken concurrently"
    private static List<CourseReq> coursesIn(Requirement requirement) {
        if (requirement instanceof CourseReq course) {
            return List.of(course);
        }
        if (requirement instanceof And and) {
            return and.parts().stream().flatMap(part -> coursesIn(part).stream()).toList();
        }
        if (requirement instanceof Or or) {
            return or.parts().stream().flatMap(part -> coursesIn(part).stream()).toList();
        }
        throw new RequisiteParseException("Only courses can be taken concurrently, not " + requirement);
    }

    // Rebuilds the tree with change applied to every course in it
    private static Requirement mapCourses(Requirement requirement, UnaryOperator<CourseReq> change) {
        if (requirement instanceof CourseReq course) {
            return change.apply(course);
        }
        if (requirement instanceof And and) {
            return new And(and.parts().stream().map(part -> mapCourses(part, change)).toList());
        }
        if (requirement instanceof Or or) {
            return new Or(or.parts().stream().map(part -> mapCourses(part, change)).toList());
        }
        return requirement;
    }
}
