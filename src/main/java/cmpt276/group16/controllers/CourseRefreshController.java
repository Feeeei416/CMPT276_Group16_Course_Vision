package cmpt276.group16.controllers;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import cmpt276.group16.courseimport.CourseImportService;
import cmpt276.group16.courseimport.CourseImportService.ImportAlreadyRunningException;
import cmpt276.group16.courseimport.CourseImportService.ImportRun;
import cmpt276.group16.courseimport.CourseRefreshScheduler;
import cmpt276.group16.courseimport.SfuApiException;
import cmpt276.group16.models.CourseRefresh;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.YearAndSeason;
import cmpt276.group16.services.PastOfferings;

// The IT manager's course refresh page: refresh one term's courses from the SFU API, 
// import past terms' course lists, and see the log of past refreshes.
@Controller 
public class CourseRefreshController {

    // One choice in the dropdown; value is what the form sends, for example "2027-SPRING"
    public record TermOption(int year, Season season) {
        public String value() {
            return year + "-" + season.name();
        }
 
        public String name() {
            return new YearAndSeason(year, season).name();
        }
    }

    // One refresh in the log. runningNow is true for the refresh running at the moment.
    // One that hasn't finished and isn't running was cut off by the app stopping
    public record LogRow(CourseRefresh refresh, String startedAt, boolean runningNow) {
        public boolean cutOff() {
            return !refresh.isFinished() && !runningNow;
        }
    }

    // for example "Nov 15, 2026, 3:10 PM", in the clock's time zone (Vancouver).
    // English rather than Canadian English, which would write "p.m."
    private static final DateTimeFormatter STARTED_AT = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.ENGLISH);
 
    private final CourseImportService importService;
    private final CourseRefreshScheduler scheduler;
    private final Clock clock;
 
    public CourseRefreshController(CourseImportService importService, CourseRefreshScheduler scheduler, Clock clock) {
        this.importService = importService;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /** GET /it/course-refresh : the refresh form and the log of past refreshes. */
    @GetMapping("/it/course-refresh")
    public String showCourseRefresh(Model model) {
        List<TermOption> terms = termOptions(LocalDate.now(clock));
        model.addAttribute("terms", terms);
        // Next semester is the one students are registering for, so it is picked to start with
        model.addAttribute("selectedTerm", terms.get(1).value());
        model.addAttribute("running", importService.isRunning());
        model.addAttribute("autoRefresh", scheduler.description());
        model.addAttribute("log", importService.refreshLog().stream()
                .map(refresh -> new LogRow(refresh, STARTED_AT.format(refresh.getStartedAt().atZone(clock.getZone())),
                        importService.isRunning(refresh)))
                .toList());
        return "it/course-refresh";
    }

    /** POST /it/course-refresh/refresh : imports the chosen term's courses from the SFU Course Outlines API. */
    @PostMapping("/it/course-refresh/refresh")
    public String refresh(@RequestParam(defaultValue = "") String term, RedirectAttributes redirectAttributes) {
        // Only the terms in the dropdown are accepted, so a changed form can't import some other term
        Optional<TermOption> chosen = termOptions(LocalDate.now(clock)).stream()
                .filter(option -> option.value().equals(term))
                .findFirst();
        if (chosen.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Choose a term from the list.");
            return "redirect:/it/course-refresh";
        }
        TermOption option = chosen.get();
        try {
            ImportRun run = importService.importTerm(option.year(), option.season());
            redirectAttributes.addFlashAttribute("message",
                    "Refreshed " + run.termName() + ": " + run.courses() + " courses and " + run.sections() + " sections.");
        } catch (SfuApiException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Couldn't get course data from SFU, so nothing was changed. Try again later.");
        } catch (ImportAlreadyRunningException e) {
            redirectAttributes.addFlashAttribute("error",
                    "A refresh is already running. Wait for it to finish, then reload this page.");
        } catch (RuntimeException e) {
            // The service has already logged it and recorded it as a failed refresh
            redirectAttributes.addFlashAttribute("error",
                    "The refresh failed, so nothing was changed. Try again, and check the server log if it keeps failing.");
        }
        return "redirect:/it/course-refresh";
    }

    /** POST /it/course-refresh/past-terms : records which courses ran in each of the last 6 terms. */
    @PostMapping("/it/course-refresh/past-terms")
    public String importPastTerms(RedirectAttributes redirectAttributes) {
        List<String> imported = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        boolean alreadyRunning = false;
        for (YearAndSeason term : PastOfferings.recentTerms(LocalDate.now(clock))) {
            try {
                importService.importPastTerm(term.year(), term.season());
                imported.add(term.name());
            } catch (ImportAlreadyRunningException e) {
                // Every term after this one would be turned down too, so stop and report what was done so far
                alreadyRunning = true;
                break;
            } catch (RuntimeException e) {
                // SfuApiException or a database error. The service has logged it, so the other terms go on
                failed.add(term.name());
            }
        }
        if (!imported.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "Imported past terms: " + String.join(", ", imported) + ".");
        }
        
        // Both problems can happen in one run, and the page shows one error, so they're joined
        List<String> errors = new ArrayList<>();
        if (!failed.isEmpty()) {
            errors.add("Couldn't import " + String.join(", ", failed) + ". The log below says why.");
        }
        if (alreadyRunning) {
            boolean triedSome = !imported.isEmpty() || !failed.isEmpty();
            errors.add("A refresh is already running, so " + (triedSome ? "the rest weren't" : "past terms weren't")
                    + " imported. Wait for it to finish, then try again.");
        }
        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", String.join(" ", errors));
        }
        return "redirect:/it/course-refresh";
    }
 
    // This term and the next two, for example 2026 Fall, 2027 Spring and 2027 Summer in November 2026.
    static List<TermOption> termOptions(LocalDate today) {
        YearAndSeason thisTerm = YearAndSeason.of(today);
        List<TermOption> options = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            YearAndSeason term = thisTerm.plus(i);
            options.add(new TermOption(term.year(), term.season()));
        }
        return options;
    }
}
