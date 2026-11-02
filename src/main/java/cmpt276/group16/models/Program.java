package cmpt276.group16.models;

public enum Program {
    COMPUTING_SCIENCE("Computing Science"),
    COMPUTER_ENGINEERING("Computer Engineering");

    // Human-readable name shown on pages
    private final String showName;

    Program(String showName) {
        this.showName = showName;
    }
    public String getShowName(){
        return showName;
    }
}
