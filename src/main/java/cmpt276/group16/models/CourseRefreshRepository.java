package cmpt276.group16.models;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

// The log of course data refreshes. Newest first everywhere: the id breaks ties between two refreshes
// that finished at the same moment, so the one saved last still comes first
public interface CourseRefreshRepository extends JpaRepository<CourseRefresh, Long> {

    // The log on the Course refresh page. A refresh a day plus a few more is roughly 400 a year,
    // so only the latest are shown
    List<CourseRefresh> findTop50ByOrderByFinishedAtDescIdDesc();

    Optional<CourseRefresh> findFirstByOrderByFinishedAtDescIdDesc();

    // Whether the term has had a refresh (automatic or not, worked or not) that finished after the given time.
    // The automatic refresh uses it to decide which terms are due
    boolean existsByTermYearAndSeasonAndFinishedAtAfter(int termYear, Season season, Instant after);
}
