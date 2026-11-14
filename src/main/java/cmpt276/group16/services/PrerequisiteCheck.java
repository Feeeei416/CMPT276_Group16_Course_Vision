package cmpt276.group16.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import cmpt276.group16.models.Grades;
import cmpt276.group16.models.NodeType;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.TranscriptCourse;

// Checks one course's prerequisite tree against a student's record.
// Used to list the courses a student can take (#38) and to say why they can't take the others (#39)
public final class PrerequisiteCheck {

    // met: the student can take the course.
    // missing: one sentence for each requirement they don't meet, for example
    //          "Needs CMPT 225 with C- or better, you have D."
    // toConfirm: things the app can't be sure of, such as a prerequisite still in progress
    //            or "permission of the instructor". The student should check these themselves
    public record Result(boolean met, List<String> missing, List<String> toConfirm) {
    }

    private PrerequisiteCheck() {
    }

    // rules: every node of one course's prerequisite tree, as returned by
    //        RequisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc. Empty means no prerequisites.
    // record: every course in the student's record
    public static Result check(List<RequisiteRule> rules, List<TranscriptCourse> record) {
        List<RequisiteRule> roots = rules.stream().filter(rule -> rule.getParent() == null).toList();
        if (roots.isEmpty()) {
            return new Result(true, List.of(), List.of());
        }

        // A tree has one root. If there are several, all of them must be met
        List<Outcome> outcomes = roots.stream().map(root -> evaluate(root, rules, record)).toList();
        Outcome whole = outcomes.size() == 1 ? outcomes.get(0) : and(outcomes);

        if (whole.unverifiable) {
            // Only something like "permission of the department": can't be checked either way
            return new Result(true, List.of(), List.of(whole.phrase));
        }
        if (whole.met) {
            return new Result(true, List.of(), whole.toConfirm);
        }
        return new Result(false, sentences(whole), List.of());
    }

    // What came out of checking one node of the tree
    private static final class Outcome {
        boolean met;
        // True for requirements the app can't check, such as "permission of the instructor"
        boolean unverifiable;
        // What is required, for example "CMPT 225 with C- or better" or "60 units"
        String phrase;
        // What the student has instead, for example "D" or "45.00"; null if nothing to show
        String have;
        // Set on an unmet AND group: the requirements inside it that aren't met
        List<Outcome> unmetParts = List.of();
        List<String> toConfirm = new ArrayList<>();
    }

    private static Outcome evaluate(RequisiteRule node, List<RequisiteRule> rules, List<TranscriptCourse> record) {
        if (node.getNodeType() == NodeType.COURSE) {
            return course(node, record);
        }
        if (node.getNodeType() == NodeType.MIN_UNITS) {
            return units(node, record);
        }
        if (node.getNodeType() == NodeType.SPECIAL) {
            Outcome special = new Outcome();
            special.unverifiable = true;
            special.phrase = node.getSpecialText();
            return special;
        }
        List<Outcome> children = rules.stream()
                .filter(rule -> rule.getParent() != null && sameNode(rule.getParent(), node))
                .map(child -> evaluate(child, rules, record))
                .toList();
        return node.getNodeType() == NodeType.AND ? and(children) : or(children);
    }

    // Saved rules are matched by id. Rules that aren't saved yet (in unit tests) are matched as the same object
    private static boolean sameNode(RequisiteRule a, RequisiteRule b) {
        return a == b || (a.getId() != null && a.getId().equals(b.getId()));
    }

    // Every part must be met. A part that can't be checked doesn't block the course, but is passed on to confirm
    private static Outcome and(List<Outcome> children) {
        Outcome result = new Outcome();
        List<Outcome> unmet = new ArrayList<>();
        for (Outcome child : children) {
            if (child.unverifiable) {
                result.toConfirm.add(child.phrase);
            } else if (child.met) {
                result.toConfirm.addAll(child.toConfirm);
            } else {
                unmet.add(child);
            }
        }
        result.met = unmet.isEmpty();
        if (!result.met) {
            result.unmetParts = unmet;
            String joined = unmet.stream().map(PrerequisiteCheck::inline).collect(Collectors.joining(" and "));
            result.phrase = unmet.size() > 1 ? "(" + joined + ")" : joined;
        } else if (!children.isEmpty() && children.stream().allMatch(child -> child.unverifiable)) {
            // Nothing in the group could be checked
            result.met = false;
            result.unverifiable = true;
            result.phrase = String.join(" and ", result.toConfirm);
            result.toConfirm = new ArrayList<>();
        }
        return result;
    }

    // One part is enough. A part that can't be checked never counts as met here,
    // otherwise "CMPT 125 or permission of the instructor" would let everyone in
    private static Outcome or(List<Outcome> children) {
        List<Outcome> met = children.stream().filter(child -> child.met).toList();
        if (!met.isEmpty()) {
            // Prefer an option with nothing left to confirm
            Optional<Outcome> certain = met.stream().filter(child -> child.toConfirm.isEmpty()).findFirst();
            Outcome result = new Outcome();
            result.met = true;
            result.toConfirm = certain.orElse(met.get(0)).toConfirm;
            return result;
        }
        Outcome result = new Outcome();
        result.phrase = children.stream().map(PrerequisiteCheck::inline).collect(Collectors.joining(" or "));
        result.unverifiable = !children.isEmpty() && children.stream().allMatch(child -> child.unverifiable);
        return result;
    }

    private static Outcome course(RequisiteRule node, List<TranscriptCourse> record) {
        String code = node.getReqSubject() + " " + node.getReqNumber();
        String minGrade = node.getMinGrade();
        List<TranscriptCourse> attempts = record.stream()
                .filter(c -> code.equals((c.getSubject() + " " + c.getNumber()).toUpperCase(Locale.ROOT)))
                .toList();

        Outcome result = new Outcome();
        result.phrase = minGrade == null ? code : code + " with " + minGrade + " or better";

        if (attempts.stream().anyMatch(c -> meetsGrade(c.getGrade(), minGrade))) {
            result.met = true;
            return result;
        }
        // A course in progress will be finished before next semester, so it counts for now
        if (attempts.stream().anyMatch(TranscriptCourse::isInProgress)) {
            result.met = true;
            result.toConfirm.add(minGrade == null
                    ? code + " is still in progress."
                    : code + " is still in progress, and you need " + minGrade + " or better.");
            return result;
        }
        result.have = bestLetterGrade(attempts);
        return result;
    }

    // Transfer credit has no letter grade, so it's accepted for any minimum
    private static boolean meetsGrade(String grade, String minGrade) {
        if (!Grades.earnsCredit(grade)) {
            return false;
        }
        if (minGrade == null || Grades.TRANSFER.equals(grade)) {
            return true;
        }
        // Grades.LETTER runs from best to worst, so a smaller index is a better grade
        int index = Grades.LETTER.indexOf(grade);
        int minIndex = Grades.LETTER.indexOf(minGrade);
        return index >= 0 && minIndex >= 0 && index <= minIndex;
    }

    // The best letter grade among the attempts, to show the student what they have; null if there is none
    private static String bestLetterGrade(List<TranscriptCourse> attempts) {
        return attempts.stream()
                .map(TranscriptCourse::getGrade)
                .filter(grade -> grade != null && Grades.LETTER.contains(grade))
                .min((a, b) -> Integer.compare(Grades.LETTER.indexOf(a), Grades.LETTER.indexOf(b)))
                .orElse(null);
    }

    private static Outcome units(RequisiteRule node, List<TranscriptCourse> record) {
        BigDecimal needed = node.getMinUnits();
        UnitTotals.Totals totals = UnitTotals.of(record);

        Outcome result = new Outcome();
        result.phrase = needed.stripTrailingZeros().toPlainString() + " units";

        if (totals.earned().compareTo(needed) >= 0) {
            result.met = true;
        } else if (totals.earned().add(totals.inProgress()).compareTo(needed) >= 0) {
            // Enough once this term's courses are finished
            result.met = true;
            result.toConfirm.add("Needs " + result.phrase + ", which you'll have after your courses in progress.");
        } else {
            result.have = totals.earned().toPlainString();
        }
        return result;
    }

    // Inside a longer sentence: "CMPT 125 with C- or better (you have D)"
    private static String inline(Outcome outcome) {
        return outcome.have == null ? outcome.phrase : outcome.phrase + " (you have " + outcome.have + ")";
    }

    // One sentence per unmet requirement. An unmet AND group gives one sentence for each part
    private static List<String> sentences(Outcome outcome) {
        if (!outcome.unmetParts.isEmpty()) {
            return outcome.unmetParts.stream().flatMap(part -> sentences(part).stream()).toList();
        }
        String have = outcome.have == null ? "" : ", you have " + outcome.have;
        return List.of("Needs " + outcome.phrase + have + ".");
    }
}