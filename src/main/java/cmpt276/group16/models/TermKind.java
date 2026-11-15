package cmpt276.group16.models;

// What a student does in a planned term. Only study terms hold courses; co-op and gap terms keep a term
// in the plan so the student can see where it falls
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

    public boolean takesCourses() {
        return this == STUDY;
    }
}
