package cmpt276.group16.courseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import cmpt276.group16.courseimport.CourseImportService.ImportAlreadyRunningException;
import cmpt276.group16.models.CourseRefresh;
import cmpt276.group16.models.CourseRefreshRepository;
import cmpt276.group16.models.Season;

// Uses the real refresh log, so these also check the query that decides what's due.
// The import itself is a mock: CourseImportServiceTest covers it, and these tests must never call SFU
@DataJpaTest
class CourseRefreshSchedulerTest {

    // November 9, 2026: this term is 2026 Fall and next semester is 2027 Spring
    private static final Instant NOW = Instant.parse("2026-11-09T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("America/Vancouver"));

    @Autowired
    private CourseRefreshRepository refreshRepository;

    private final CourseImportService importService = mock(CourseImportService.class);

    // Built here rather than by Spring, which has it turned off for tests
    private CourseRefreshScheduler scheduler(boolean enabled) {
        return new CourseRefreshScheduler(importService, refreshRepository, CLOCK, enabled,
                Duration.ofDays(1), Duration.ofDays(7));
    }

    // A refresh of the term that finished the given time ago, after running for 5 minutes
    private CourseRefresh finished(int year, Season season, Duration ago, boolean automatic) {
        Instant finishedAt = NOW.minus(ago);
        CourseRefresh refresh = new CourseRefresh(year, season, automatic, finishedAt.minus(Duration.ofMinutes(5)));
        refresh.finish(finishedAt, 52, 310, 0, 0, List.of());
        return refreshRepository.save(refresh);
    }

    private void neverRefreshes() {
        verify(importService, never()).importTermAutomatically(anyInt(), any());
    }

    // -- What's due --

    // With nothing in the log both terms are due, and next semester goes first
    @Test
    void nothingLoggedRefreshesBothNextSemesterFirst() {
        scheduler(true).refreshDueTerms();

        InOrder order = inOrder(importService);
        order.verify(importService).importTermAutomatically(2027, Season.SPRING);
        order.verify(importService).importTermAutomatically(2026, Season.FALL);
    }

    @Test
    void recentRefreshesMeanNothingIsDue() {
        finished(2027, Season.SPRING, Duration.ofHours(23), true);
        finished(2026, Season.FALL, Duration.ofDays(6), true);

        scheduler(true).refreshDueTerms();

        neverRefreshes();
    }

    @Test
    void nextSemesterIsDueADayAfterItsLastRefresh() {
        finished(2027, Season.SPRING, Duration.ofHours(25), true);
        finished(2026, Season.FALL, Duration.ofDays(1), true);

        scheduler(true).refreshDueTerms();

        verify(importService).importTermAutomatically(2027, Season.SPRING);
        verify(importService, never()).importTermAutomatically(2026, Season.FALL);
    }

    @Test
    void thisTermIsDueAWeekAfterItsLastRefresh() {
        finished(2027, Season.SPRING, Duration.ofHours(1), true);
        finished(2026, Season.FALL, Duration.ofDays(8), true);

        scheduler(true).refreshDueTerms();

        verify(importService).importTermAutomatically(2026, Season.FALL);
        verify(importService, never()).importTermAutomatically(2027, Season.SPRING);
    }

    // A refresh an IT manager just ran isn't repeated
    @Test
    void manualRefreshCounts() {
        finished(2027, Season.SPRING, Duration.ofHours(1), false);
        finished(2026, Season.FALL, Duration.ofHours(1), false);

        scheduler(true).refreshDueTerms();

        neverRefreshes();
    }

    // A term that failed an hour ago (SFU hasn't published it, for example) waits a day like any other
    @Test
    void failedRefreshCounts() {
        CourseRefresh failed = new CourseRefresh(2027, Season.SPRING, true, NOW.minus(Duration.ofHours(1)));
        failed.fail(NOW.minus(Duration.ofHours(1)), "SFU hasn't published 2027 Spring yet");
        refreshRepository.save(failed);
        finished(2026, Season.FALL, Duration.ofDays(1), true);

        scheduler(true).refreshDueTerms();

        neverRefreshes();
    }

    // A refresh cut off by the app stopping never finished, so the term is tried again
    @Test
    void refreshThatDidntFinishIsTriedAgain() {
        refreshRepository.save(new CourseRefresh(2027, Season.SPRING, true, NOW.minus(Duration.ofHours(1))));
        finished(2026, Season.FALL, Duration.ofDays(1), true);

        scheduler(true).refreshDueTerms();

        verify(importService).importTermAutomatically(2027, Season.SPRING);
    }

    // Refreshing 2027 Summer doesn't make 2027 Spring up to date
    @Test
    void otherTermsDontCount() {
        finished(2027, Season.SUMMER, Duration.ofHours(1), true);
        finished(2026, Season.FALL, Duration.ofDays(1), true);

        scheduler(true).refreshDueTerms();

        verify(importService).importTermAutomatically(2027, Season.SPRING);
    }

    // -- When a refresh doesn't work --

    // The failure is already in the log, so the scheduler just moves on to this term
    @Test
    void failedNextSemesterStillRefreshesThisTerm() {
        when(importService.importTermAutomatically(2027, Season.SPRING))
                .thenThrow(new SfuApiException("SFU is down", null));

        scheduler(true).refreshDueTerms();

        verify(importService).importTermAutomatically(2026, Season.FALL);
    }

    // An IT manager's refresh is running: this one is skipped quietly and tried on the next check
    @Test
    void refreshAlreadyRunningIsSkipped() {
        when(importService.importTermAutomatically(2027, Season.SPRING)).thenThrow(new ImportAlreadyRunningException());

        scheduler(true).refreshDueTerms();

        verify(importService).importTermAutomatically(2026, Season.FALL);
    }

    // -- Turning it off --

    @Test
    void checkRefreshesWhenTurnedOn() {
        scheduler(true).check();

        verify(importService).importTermAutomatically(2027, Season.SPRING);
    }

    // Tests and local runs can turn it off, so nothing calls SFU on its own
    @Test
    void checkDoesNothingWhenTurnedOff() {
        scheduler(false).check();

        verifyNoInteractions(importService);
    }

    // -- What the page says --

    @Test
    void describesHowOftenEachTermIsRefreshed() {
        assertThat(scheduler(true).description())
                .startsWith("Next semester is also refreshed automatically once a day, and this term once a week.");
        assertThat(scheduler(false).description())
                .isEqualTo("Automatic refresh is turned off, so terms are only refreshed from this page.");
    }

    @ParameterizedTest
    @CsvSource({
        "PT24H, once a day",
        "P7D, once a week",
        "P3D, every 3 days",
        "PT12H, every 12 hours",
        "PT1H, once an hour",
        "PT30M, every 30 minutes"
    })
    void saysHowOften(Duration every, String text) {
        assertThat(CourseRefreshScheduler.howOften(every)).isEqualTo(text);
    }
}
