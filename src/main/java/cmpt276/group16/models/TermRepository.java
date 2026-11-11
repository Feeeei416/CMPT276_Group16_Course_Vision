package cmpt276.group16.models;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, Long> {

    boolean existsByTermYearAndSeason(Integer termYear, Season season);

    // Used when saving offerings, to reuse the term if it already exists
    Optional<Term> findByTermYearAndSeason(Integer termYear, Season season);
}