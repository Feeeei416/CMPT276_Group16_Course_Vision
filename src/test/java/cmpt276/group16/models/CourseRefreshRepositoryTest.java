package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class CourseRefreshRepositoryTest {

    private static final Instant NOV_9 = Instant.parse("2026-11-09T20:00:00Z");

    @Autowired
    private CourseRefreshRepository refreshRepository;

    @Autowired
    private TestEntityManager entityManager;

    // A manual refresh that started at the given time and worked, taking 5 minutes
    private CourseRefresh worked(int year, Season season, Instant startedAt) {
        CourseRefresh refresh = new CourseRefresh(year, season, false, startedAt);
        refresh.finish(startedAt.plus(5, ChronoUnit.MINUTES), 52, 310, 2, 1, List.of());
        return refreshRepository.save(refresh);
    }

    private CourseRefresh reload(CourseRefresh refresh) {
        entityManager.flush();
        entityManager.clear();
        return refreshRepository.findById(refresh.getId()).orElseThrow();
    }

    // Everything about a refresh that worked comes back the same after it's saved, including the skipped notes
    @Test
    void finishedRefreshIsSavedAndLoadedBack() {
        CourseRefresh refresh = new CourseRefresh(2027, Season.SPRING, true, NOV_9);
        refresh.finish(NOV_9.plus(5, ChronoUnit.MINUTES), 52, 310, 2, 1,
                List.of("MATH 495 has no outline, skipped", "CMPT 105W has no sections, skipped"));

        CourseRefresh found = reload(refreshRepository.save(refresh));
        assertThat(found.getTermName()).isEqualTo("2027 Spring");
        assertThat(found.isAutomatic()).isTrue();
        assertThat(found.getStartedAt()).isEqualTo(NOV_9);
        assertThat(found.getFinishedAt()).isEqualTo(NOV_9.plus(5, ChronoUnit.MINUTES));
        assertThat(found.isFinished()).isTrue();
        assertThat(found.getCourses()).isEqualTo(52);
        assertThat(found.getSections()).isEqualTo(310);
        assertThat(found.getRemovedSections()).isEqualTo(2);
        assertThat(found.getNotOffered()).isEqualTo(1);
        assertThat(found.getNotes())
                .containsExactly("MATH 495 has no outline, skipped", "CMPT 105W has no sections, skipped");
        assertThat(found.isFailed()).isFalse();
    }

    // A failed refresh keeps its error and counts as finished. Having no notes reads back as an empty list
    @Test
    void failedRefreshKeepsItsError() {
        CourseRefresh refresh = new CourseRefresh(2027, Season.SUMMER, false, NOV_9);
        refresh.fail(NOV_9.plus(1, ChronoUnit.MINUTES), "SFU hasn't published 2027 Summer yet");

        CourseRefresh found = reload(refreshRepository.save(refresh));
        assertThat(found.isFailed()).isTrue();
        assertThat(found.isFinished()).isTrue();
        assertThat(found.getError()).isEqualTo("SFU hasn't published 2027 Summer yet");
        assertThat(found.getNotes()).isEmpty();
    }

    // A refresh that has only started has no finish time and no error
    @Test
    void startedRefreshHasNotFinished() {
        CourseRefresh found = reload(refreshRepository.save(new CourseRefresh(2027, Season.SPRING, false, NOV_9)));

        assertThat(found.isFinished()).isFalse();
        assertThat(found.isFailed()).isFalse();
        assertThat(found.getCourses()).isZero();
    }

    // The log is newest first by start time, whatever order the refreshes were saved in.
    // One that never finished is listed where it started
    @Test
    void logIsNewestFirst() {
        CourseRefresh older = worked(2026, Season.FALL, NOV_9.minus(2, ChronoUnit.DAYS));
        CourseRefresh newest = worked(2027, Season.SPRING, NOV_9);
        CourseRefresh unfinished = refreshRepository.save(
                new CourseRefresh(2027, Season.SPRING, true, NOV_9.minus(1, ChronoUnit.DAYS)));

        assertThat(refreshRepository.findTop50ByOrderByStartedAtDescIdDesc()).containsExactly(newest, unfinished, older);
    }

    // Two refreshes that started at the same moment: the one saved last comes first
    @Test
    void sameStartTimeListsTheLastSavedFirst() {
        CourseRefresh first = worked(2026, Season.FALL, NOV_9);
        CourseRefresh second = worked(2027, Season.SPRING, NOV_9);

        assertThat(refreshRepository.findTop50ByOrderByStartedAtDescIdDesc()).containsExactly(second, first);
    }

    // Only the latest 50 are listed
    @Test
    void logListsTheLatestFifty() {
        for (int i = 0; i < 51; i++) {
            worked(2027, Season.SPRING, NOV_9.minus(i, ChronoUnit.HOURS));
        }

        List<CourseRefresh> log = refreshRepository.findTop50ByOrderByStartedAtDescIdDesc();
        assertThat(log).hasSize(50);
        assertThat(log.get(49).getStartedAt()).isEqualTo(NOV_9.minus(49, ChronoUnit.HOURS));
    }

    // Only a refresh of the same term that finished after the time counts
    @Test
    void existsOnlyForTheSameTermFinishedAfterTheTime() {
        worked(2027, Season.SPRING, NOV_9);
        Instant finished = NOV_9.plus(5, ChronoUnit.MINUTES);
        Instant dayBefore = NOV_9.minus(1, ChronoUnit.DAYS);

        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SPRING, dayBefore)).isTrue();
        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SPRING, finished)).isFalse();
        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SUMMER, dayBefore)).isFalse();
        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2028, Season.SPRING, dayBefore)).isFalse();
    }

    // A failed refresh counts too, so a term SFU hasn't published isn't tried again on every check
    @Test
    void failedRefreshCountsForExists() {
        CourseRefresh refresh = new CourseRefresh(2027, Season.SUMMER, true, NOV_9);
        refresh.fail(NOV_9, "SFU is down");
        refreshRepository.save(refresh);

        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SUMMER,
                NOV_9.minus(1, ChronoUnit.DAYS))).isTrue();
    }

    // A refresh that never finished doesn't count, so the term is tried again
    @Test
    void unfinishedRefreshDoesntCountForExists() {
        refreshRepository.save(new CourseRefresh(2027, Season.SPRING, true, NOV_9));

        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SPRING,
                NOV_9.minus(1, ChronoUnit.DAYS))).isFalse();
    }
}
