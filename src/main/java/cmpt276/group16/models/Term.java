package cmpt276.group16.models;

import jakarta.persistence.*;

@Entity 
@Table (name = "terms",
        uniqueConstraints = @UniqueConstraint(columnNames = {"term_year", "season"}))
public class Term {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // for example 2024
    @Column(nullable = false)
    private Integer termYear;

    // Store as string
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Season season;

    protected Term() {}

    public Term(Integer termYear, Season season) {
        this.termYear = termYear;
        this.season = season;
    }

    /** No setters: a term like "2024 Fall" never changes once created
     *  a different year or season is a different term
     */
    public Long getId() {
        return id;
    }

    public Integer getTermYear() {
        return termYear;
    }

    public Season getSeason() {
        return season;
    }

}
