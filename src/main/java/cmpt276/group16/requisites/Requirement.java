package cmpt276.group16.requisites;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import cmpt276.group16.models.Grades;

// One part of a prerequisite, as read from the Calendar text and before it is saved as RequisiteRule rows.
// The parser builds these instead of entities so it can be tested without a database.
// The checks below are what PrerequisiteCheck relies on, so a bad tree fails here instead of in front of a student
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

    // A course, for example CMPT 125 with C- or better. minGrade is null if any passing grade counts
    record CourseReq(String subject, String number, String minGrade) implements Requirement {
        public CourseReq {
            if (minGrade != null && !Grades.LETTER.contains(minGrade)) {
                throw new IllegalArgumentException("Not a letter grade: " + minGrade);
            }
        }

        // "CMPT 125" or "CMPT 125 (C-)"
        @Override
        public String toString() {
            String code = subject + " " + number;
            return minGrade == null ? code : code + " (" + minGrade + ")";
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
