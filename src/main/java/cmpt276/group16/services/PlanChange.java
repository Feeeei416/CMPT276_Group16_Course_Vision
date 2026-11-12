package cmpt276.group16.services;

import java.util.List;

import cmpt276.group16.models.PlanTerm;

// What one change to a plan did: a message saying what was saved, or the problems that stopped it.
// termId is the term the change happened in, so the page can show the message there. It's null when there's no
// term to show it in, like after a whole term was removed
public record PlanChange(String message, List<String> problems, Long termId) {

    // For example "Removed 2027 Fall."
    public static PlanChange saved(String message) {
        return new PlanChange(message, List.of(), null);
    }

    // For example "Added CMPT 225 to 2027 Fall."
    public static PlanChange saved(String message, PlanTerm term) {
        return new PlanChange(message, List.of(), term.getId());
    }

    public static PlanChange rejected(String problem) {
        return new PlanChange(null, List.of(problem), null);
    }

    public static PlanChange rejected(List<String> problems) {
        return new PlanChange(null, problems, null);
    }

    public static PlanChange rejected(String problem, PlanTerm term) {
        return new PlanChange(null, List.of(problem), term.getId());
    }

    public boolean isSaved() {
        return problems.isEmpty();
    }
}
