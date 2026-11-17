package cmpt276.group16.models;

// What a student does in a planned term. Study terms hold normal courses, co-op terms hold the co-op
// work term course (like CMPT 426) and at most one other course, and gap terms hold nothing; they keep the term
// in the plan so the student can see where it falls. PlanRules.isCoopCourse says which courses are co-op work terms
public enum TermKind {
    STUDY("Study"),
    COOP("Co-op"),
    GAP("Gap");

    // Human-readable name shown on pages
    private final String showName;

    TermKind(String showName) {
        this.showName = showName;
    }

    public String getShowName() {
        return showName;
    }

    // False only for gap terms. Which courses a study or co-op term takes is checked in PlanService
    public boolean takesCourses() {
        return this != GAP;
    }
}
