package cmpt276.group16.models;

import java.util.Comparator;
import java.util.Locale;

import jakarta.persistence.*;

// One term in a plan, such as 2027 Fall. A plan can't have the same term twice
@Entity
@Table(name = "plan_terms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "term_id"}))
public class PlanTerm {

    // Oldest first. Season is declared in the order the seasons happen, so it sorts Spring, Summer, Fall
    public static final Comparator<PlanTerm> OLDEST_FIRST = Comparator
            .comparing((PlanTerm planTerm) -> planTerm.getTerm().getTermYear())
            .thenComparing(planTerm -> planTerm.getTerm().getSeason());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many terms belong to one plan
    @ManyToOne
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    // The shared row in terms, the same one course offerings use
    @ManyToOne
    @JoinColumn(name = "term_id", nullable = false)
    private Term term;

    protected PlanTerm() {}

    public PlanTerm(Plan plan, Term term) {
        this.plan = plan;
        this.term = term;
    }

    public Long getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public Term getTerm() {
        return term;
    }

    // For example "2027 Fall", the same format as transcript terms
    public String getName() {
        return nameOf(term.getTermYear(), term.getSeason());
    }

    public static String nameOf(int year, Season season) {
        String name = season.name();
        return year + " " + name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
