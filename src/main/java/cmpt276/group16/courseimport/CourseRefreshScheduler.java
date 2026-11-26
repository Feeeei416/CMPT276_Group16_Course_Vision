package cmpt276.group16.courseimport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cmpt276.group16.courseimport.CourseImportService.ImportAlreadyRunningException;
import cmpt276.group16.models.CourseRefreshRepository;
import cmpt276.group16.models.YearAndSeason;
import cmpt276.group16.services.PlanRules;

// Refreshes next semester's and this term's course data on its own
// It checks the refresh log (app.course-refresh.* in application.properties) instead of running at
// a set time of day. Free Render puts the app to sleep after 15 minutes without visitors, so something like a job at 3 AM would
// hardly ever run, but a refresh that came due while the app was asleep still happens on the first check after
// someone wakes it up
@Component
public class CourseRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(CourseRefreshScheduler.class);

    private final CourseImportService importService;
    private final CourseRefreshRepository refreshRepository;
    private final Clock clock;
    private final boolean enabled;
    private final Duration nextSemesterEvery;
    private final Duration currentTermEvery;

    public CourseRefreshScheduler(CourseImportService importService, CourseRefreshRepository refreshRepository,
            Clock clock,
            @Value("${app.course-refresh.automatic}") boolean enabled,
            @Value("${app.course-refresh.next-semester-every}") Duration nextSemesterEvery,
            @Value("${app.course-refresh.current-term-every}") Duration currentTermEvery) {
        this.importService = importService;
        this.refreshRepository = refreshRepository;
        this.clock = clock;
        this.enabled = enabled;
        this.nextSemesterEvery = nextSemesterEvery;
        this.currentTermEvery = currentTermEvery;
    }

    // Runs on Spring's scheduler thread, a little after the app starts and then every so often.
    // A fixed delay counts from when the last check ended, so a long refresh never overlaps the next check
    @Scheduled(initialDelayString = "${app.course-refresh.first-check-after}",
            fixedDelayString = "${app.course-refresh.check-every}")
    public void check() {
        if (enabled) {
            refreshDueTerms();
        }
    }

    // Next semester goes first, since it's the one students are registering for
    public void refreshDueTerms() {
        LocalDate today = LocalDate.now(clock);
        refreshIfDue(YearAndSeason.of(today).next(), nextSemesterEvery);
        refreshIfDue(YearAndSeason.of(today), currentTermEvery);
    }

    // Due when no refresh of the term has finished within the given time. A manual or failed refresh counts too,
    // so a refresh an IT manager just ran isn't repeated, and a term SFU hasn't published fails once a day
    // instead of on every check
    private void refreshIfDue(YearAndSeason term, Duration every) {
        Instant since = Instant.now(clock).minus(every);
        if (refreshRepository.existsByTermYearAndSeasonAndFinishedAtAfter(term.year(), term.season(), since)) {
            return;
        }
        try {
            importService.importTermAutomatically(term.year(), term.season());
        } catch (ImportAlreadyRunningException e) {
            // An IT manager's refresh is running. Nothing is logged for this term, so the next check tries again
            log.info("Automatic refresh of {} {} skipped: another refresh is running", term.year(), term.season());
        } catch (RuntimeException e) {
            // The import service has already logged it (in the refresh log and the server log).
            // Caught so the other term still gets its turn
        }
    }

    // For the Course refresh page, for example "Next semester is also refreshed automatically once a day,
    // and this term once a week."
    public String description() {
        if (!enabled) {
            return "Automatic refresh is turned off, so terms are only refreshed from this page.";
        }
        return "Next semester is also refreshed automatically " + howOften(nextSemesterEvery)
                + ", and this term " + howOften(currentTermEvery)
                + ". That only happens while the site is being used, so a refresh that comes due overnight"
                + " starts soon after the next visit.";
    }

    // "once a day", "once a week", "every 3 days", "every 12 hours" or "every 30 minutes"
    static String howOften(Duration every) {
        if (every.toMinutes() % 60 != 0) {
            return "every " + every.toMinutes() + " minutes";
        }
        long hours = every.toHours();
        if (hours % 24 != 0) {
            return hours == 1 ? "once an hour" : "every " + hours + " hours";
        }
        long days = every.toDays();
        if (days == 1) {
            return "once a day";
        }
        if (days == 7) {
            return "once a week";
        }
        return "every " + days + " days";
    }
}
