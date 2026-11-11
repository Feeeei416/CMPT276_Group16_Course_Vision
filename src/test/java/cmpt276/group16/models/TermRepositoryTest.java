package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest 
class TermRepositoryTest {
    
    @Autowired
    private TermRepository termRepository;

    @Autowired
    private TestEntityManager entityManager;

    // A saved term can be loaded back from the database by its year and season
    @Test
    void termIsSavedAndFoundByYearAndSeason() {
        Term saved = termRepository.saveAndFlush(new Term(2024, Season.FALL));
        entityManager.clear();

        Term found = termRepository.findByTermYearAndSeason(2024, Season.FALL).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getTermYear()).isEqualTo(2024);
        assertThat(found.getSeason()).isEqualTo(Season.FALL);
    }

    // exists is true only when both the year and the season match a saved term
    @Test
    void existsOnlyForSavedTerm() {
        termRepository.saveAndFlush(new Term(2024, Season.FALL));

        assertThat(termRepository.existsByTermYearAndSeason(2024, Season.FALL)).isTrue();
        assertThat(termRepository.existsByTermYearAndSeason(2024, Season.SPRING)).isFalse();
        assertThat(termRepository.existsByTermYearAndSeason(2025, Season.FALL)).isFalse();
    }

    // Looking up a term that was never saved gives an empty result instead of an error
    @Test
    void findReturnsEmptyForMissingTerm() {
        assertThat(termRepository.findByTermYearAndSeason(2024, Season.FALL)).isEmpty();
    }

    // The same year and season can't be saved twice
    @Test
    void rejectsDuplicateTerm() {
        termRepository.saveAndFlush(new Term(2024, Season.FALL));

        assertThatThrownBy(() -> termRepository.saveAndFlush(new Term(2024, Season.FALL)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // The unique rule is on year and season together, so the same year or season can repeat on its own
    @Test
    void allowsSameYearOrSameSeasonInDifferentTerms() {
        termRepository.saveAndFlush(new Term(2024, Season.FALL));
        termRepository.saveAndFlush(new Term(2024, Season.SPRING));
        termRepository.saveAndFlush(new Term(2024, Season.SUMMER));

        assertThat(termRepository.count()).isEqualTo(3);
    }

    // A term must have a year
    @Test
    void rejectsMissingYear() {
        assertThatThrownBy(() -> termRepository.saveAndFlush(new Term(null, Season.FALL)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // A term must have a season
    @Test
    void rejectsMissingSeason() {
        assertThatThrownBy(() -> termRepository.saveAndFlush(new Term(2024, null)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
