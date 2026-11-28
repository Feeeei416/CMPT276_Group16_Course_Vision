package cmpt276.group16.graduation;

import java.math.BigDecimal;
import java.util.List;

import cmpt276.group16.models.GraduationRequirement;
import cmpt276.group16.models.RequirementSet;

// A student's Graduation Report (#69): each requirement of their program and entry year with how far along it is,
// grouped under the Calendar's headings, and the units completed, planned and still needed.
// Made by RequirementCheck; nothing in it is saved
public record GraduationReport(RequirementSet set, List<Category> categories, Units units) {

    // label: what the page shows. badgeClass: green when completed, red when remaining, grey otherwise
    public enum Status {
        COMPLETED("Completed", "badge"),
        IN_PROGRESS("In progress", "badge badge-muted"),
        PLANNED("Planned", "badge badge-muted"),
        REMAINING("Remaining", "badge badge-remaining"),
        // A NOTE rule the app can't check from courses, like a GPA
        CHECK("Check with an advisor", "badge badge-muted");

        private final String label;
        private final String badgeClass;

        Status(String label, String badgeClass) {
            this.label = label;
            this.badgeClass = badgeClass;
        }

        public String getLabel() {
            return label;
        }

        public String getBadgeClass() {
            return badgeClass;
        }
    }

    // One Calendar heading, like "Lower division", and its requirements in order
    public record Category(String name, List<Result> results) {
    }

    // One requirement.
    // name: what the student reads, for example "CMPT 225", "Calculus I" or "Writing".
    // progress: how much is done for rules that count courses, areas or units, for example "6 of 12 units";
    //           null for a single required course and for notes.
    // options: for a choice that isn't met yet, the courses to choose from; null when there are too many to list.
    // note: for a WQB rule the app can't settle, the courses it has no data for; otherwise null.
    // courses: the courses that count toward it, best first
    public record Result(GraduationRequirement requirement, String name, Status status, String progress,
                         String options, String note, List<StudentCourse> courses) {
    }

    // Units toward the degree, leaving out co-op work terms.
    // required: the program's total units, or null if the set has no total
    public record Units(BigDecimal completed, BigDecimal inProgress, BigDecimal planned, BigDecimal required) {

        // What's still needed after everything completed, in progress and planned. Never below zero
        public BigDecimal needed() {
            if (required == null) {
                return null;
            }
            BigDecimal left = required.subtract(completed).subtract(inProgress).subtract(planned);
            return left.signum() < 0 ? BigDecimal.ZERO : left;
        }
    }
}
