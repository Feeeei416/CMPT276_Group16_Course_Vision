package cmpt276.group16.models;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// The log of course data refreshes
public interface CourseRefreshRepository extends JpaRepository<CourseRefresh, Long> {

    // The log on the Course refresh page, newest first. A refresh a day plus a few more is roughly 400 a year,
    // so only the latest are shown.
    // Sorted by start time, since a refresh that didn't finish has no finish time (and H2 and Postgres put
    // nulls at opposite ends). The id breaks ties, so the one saved last still comes first
    List<CourseRefresh> findTop50ByOrderByStartedAtDescIdDesc();

    // Whether the term has had a refresh (automatic or not, worked or not) that finished after the given time.
    // One that never finished doesn't count, so it's tried again.
    // The automatic refresh uses it to decide which terms are due
    boolean existsByTermYearAndSeasonAndFinishedAtAfter(int termYear, Season season, Instant after);
}
