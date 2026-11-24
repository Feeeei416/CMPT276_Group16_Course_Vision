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

    private CourseRefresh worked(int year, Season season, Instant finishedAt) {
        return refreshRepository.save(new CourseRefresh(year, season, false, finishedAt, 52, 310, 2, 1, List.of(), null));
    }

    // Everything about a refresh comes back the same after it's saved, including the skipped notes
    @Test
    void refreshIsSavedAndLoadedBack() {
        Long id = refreshRepository.save(new CourseRefresh(2027, Season.SPRING, true, NOV_9, 52, 310, 2, 1,
                List.of("MATH 495 has no outline, skipped", "CMPT 105W has no sections, skipped"), null)).getId();
        entityManager.flush();
        entityManager.clear();

        CourseRefresh found = refreshRepository.findById(id).orElseThrow();
        assertThat(found.getTermName()).isEqualTo("2027 Spring");
        assertThat(found.isAutomatic()).isTrue();
        assertThat(found.getFinishedAt()).isEqualTo(NOV_9);
        assertThat(found.getCourses()).isEqualTo(52);
        assertThat(found.getSections()).isEqualTo(310);
        assertThat(found.getRemovedSections()).isEqualTo(2);
        assertThat(found.getNotOffered()).isEqualTo(1);
        assertThat(found.getNotes())
                .containsExactly("MATH 495 has no outline, skipped", "CMPT 105W has no sections, skipped");
        assertThat(found.isFailed()).isFalse();
    }

    // A failed refresh keeps its error, and having no notes reads back as an empty list
    @Test
    void failedRefreshKeepsItsError() {
        Long id = refreshRepository.save(new CourseRefresh(2027, Season.SUMMER, false, NOV_9, 0, 0, 0, 0,
                List.of(), "SFU hasn't published 2027 Summer yet")).getId();
        entityManager.flush();
        entityManager.clear();

        CourseRefresh found = refreshRepository.findById(id).orElseThrow();
        assertThat(found.isFailed()).isTrue();
        assertThat(found.getError()).isEqualTo("SFU hasn't published 2027 Summer yet");
        assertThat(found.getNotes()).isEmpty();
    }

    // The log is newest first, whatever order the refreshes were saved in
    @Test
    void logIsNewestFirst() {
        CourseRefresh older = worked(2026, Season.FALL, NOV_9.minus(2, ChronoUnit.DAYS));
        CourseRefresh newest = worked(2027, Season.SPRING, NOV_9);
        CourseRefresh middle = worked(2027, Season.SPRING, NOV_9.minus(1, ChronoUnit.DAYS));

        assertThat(refreshRepository.findTop50ByOrderByFinishedAtDescIdDesc()).containsExactly(newest, middle, older);
        assertThat(refreshRepository.findFirstByOrderByFinishedAtDescIdDesc()).contains(newest);
    }

    // Two refreshes that finished at the same moment: the one saved last comes first
    @Test
    void sameFinishTimeListsTheLastSavedFirst() {
        CourseRefresh first = worked(2026, Season.FALL, NOV_9);
        CourseRefresh second = worked(2027, Season.SPRING, NOV_9);

        assertThat(refreshRepository.findTop50ByOrderByFinishedAtDescIdDesc()).containsExactly(second, first);
    }

    // Only the latest 50 are listed
    @Test
    void logListsTheLatestFifty() {
        for (int i = 0; i < 51; i++) {
            worked(2027, Season.SPRING, NOV_9.minus(i, ChronoUnit.HOURS));
        }

        List<CourseRefresh> log = refreshRepository.findTop50ByOrderByFinishedAtDescIdDesc();
        assertThat(log).hasSize(50);
        assertThat(log.get(49).getFinishedAt()).isEqualTo(NOV_9.minus(49, ChronoUnit.HOURS));
    }

    // Nothing saved yet means there's no latest refresh
    @Test
    void noRefreshesMeansNoLatest() {
        assertThat(refreshRepository.findFirstByOrderByFinishedAtDescIdDesc()).isEmpty();
        assertThat(refreshRepository.findTop50ByOrderByFinishedAtDescIdDesc()).isEmpty();
    }

    // Only a refresh of the same term that finished after the time counts
    @Test
    void existsOnlyForTheSameTermAfterTheTime() {
        worked(2027, Season.SPRING, NOV_9);
        Instant dayBefore = NOV_9.minus(1, ChronoUnit.DAYS);

        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SPRING, dayBefore)).isTrue();
        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SPRING, NOV_9)).isFalse();
        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SUMMER, dayBefore)).isFalse();
        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2028, Season.SPRING, dayBefore)).isFalse();
    }

    // A failed refresh counts too, so a term SFU hasn't published isn't tried again every hour
    @Test
    void failedRefreshCountsForExists() {
        refreshRepository.save(new CourseRefresh(2027, Season.SUMMER, true, NOV_9, 0, 0, 0, 0, List.of(), "SFU is down"));

        assertThat(refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(2027, Season.SUMMER,
                NOV_9.minus(1, ChronoUnit.DAYS))).isTrue();
    }
}
