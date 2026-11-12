package cmpt276.group16.services;

import java.util.List;

// What one change to a plan did: a message saying what was saved, or the problems that stopped it
public record PlanChange(String message, List<String> problems) {

    // For example "Added CMPT 225 to 2027 Fall."
    public static PlanChange saved(String message) {
        return new PlanChange(message, List.of());
    }

    public static PlanChange rejected(String problem) {
        return new PlanChange(null, List.of(problem));
    }

    public static PlanChange rejected(List<String> problems) {
        return new PlanChange(null, problems);
    }

    public boolean isSaved() {
        return problems.isEmpty();
    }
}
