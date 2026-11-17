package cmpt276.group16.models;

// Declared in the order the seasons happen within a year
public enum Season {
    SPRING("Spring"),
    SUMMER("Summer"),
    FALL("Fall");

    // Human-readable name shown on pages, the same spelling transcript terms use
    private final String showName;

    Season(String showName) {
        this.showName = showName;
    }

    public String getShowName() {
        return showName;
    }
}
