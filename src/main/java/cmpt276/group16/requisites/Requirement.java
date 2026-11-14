package cmpt276.group16.requisites;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cmpt276.group16.models.Grades;

// One part of a prerequisite, as read from the Calendar text and before it is saved as RequisiteRule rows.
// The parser builds these instead of entities so it can be tested without a database.
// The checks below stop a tree that could never be met from being saved, like an empty OR or a grade SFU doesn't have
public sealed interface Requirement {

    // Every part must be met. Always two or more parts: a group of one is just that part
    record And(List<Requirement> parts) implements Requirement {
        public And {
            parts = groupOf(parts);
        }

        @Override
        public String toString() {
            return "AND(" + join(parts) + ")";
        }
    }

    // One part is enough. Always two or more parts
    record Or(List<Requirement> parts) implements Requirement {
        public Or {
            parts = groupOf(parts);
        }

        @Override
        public String toString() {
            return "OR(" + join(parts) + ")";
        }
    }

    // A course, for example CMPT 125 with C- or better. minGrade is null if any passing grade counts.
    // concurrentOk: the course can also be taken in the same term ("MATH 260 may be taken concurrently.")
    record CourseReq(String subject, String number, String minGrade, boolean concurrentOk) implements Requirement {
        public CourseReq {
            if (minGrade != null && !Grades.LETTER.contains(minGrade)) {
                throw new IllegalArgumentException("Not a letter grade: " + minGrade);
            }
        }

        public CourseReq(String subject, String number, String minGrade) {
            this(subject, number, minGrade, false);
        }

        // "CMPT 125", "CMPT 125 (C-)" or "MATH 260 (C-, concurrent)"
        @Override
        public String toString() {
            String notes = Stream.of(minGrade, concurrentOk ? "concurrent" : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            String code = subject + " " + number;
            return notes.isEmpty() ? code : code + " (" + notes + ")";
        }
    }

    // At least this many units completed, for example "60 units"
    record MinUnits(BigDecimal units) implements Requirement {
        public MinUnits {
            if (units == null || units.signum() <= 0) {
                throw new IllegalArgumentException("Units must be more than 0: " + units);
            }
        }

        @Override
        public String toString() {
            return units.stripTrailingZeros().toPlainString() + " units";
        }
    }

    // Text that can't be checked, for example "permission of the instructor"
    record Special(String text) implements Requirement {
        public Special {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Special requirement needs text");
            }
        }

        @Override
        public String toString() {
            return "SPECIAL(" + text + ")";
        }
    }

    private static List<Requirement> groupOf(List<Requirement> parts) {
        if (parts.size() < 2) {
            throw new IllegalArgumentException("A group needs at least two parts: " + parts);
        }
        return List.copyOf(parts);
    }

    private static String join(List<Requirement> parts) {
        return parts.stream().map(Requirement::toString).collect(Collectors.joining(", "));
    }
}
