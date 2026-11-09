package cmpt276.group16.models;

import java.util.Comparator;
import java.util.List;

import jakarta.persistence.*;

// One term in a plan, such as 2027 Fall. A plan can't have the same term twice
@Entity
@Table(name = "plan_terms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "term_year", "season"}))
public class PlanTerm {

    // In the order they happen within a year, spelled the way transcript terms are
    public static final List<String> SEASONS = List.of("Spring", "Summer", "Fall");

    // Oldest first. Seasons sort by when they happen, not alphabetically
    public static final Comparator<PlanTerm> OLDEST_FIRST = Comparator.comparingInt(PlanTerm::getYear)
            .thenComparingInt(term -> SEASONS.indexOf(term.getSeason()));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many terms belong to one plan
    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // "year" is a reserved word in some databases
    @Column(name = "term_year", nullable = false)
    private int year;

    @Column(nullable = false)
    private String season;

    protected PlanTerm() {}

    public PlanTerm(Plan plan, int year, String season) {
        if (!SEASONS.contains(season)) {
            throw new IllegalArgumentException("Unknown season: " + season);
        }
        this.plan = plan;
        this.year = year;
        this.season = season;
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public int getYear() {
        return year;
    }

    public String getSeason() {
        return season;
    }

    // For example "2027 Fall", the same format as transcript terms
    public String getName() {
        return year + " " + season;
    }
}
