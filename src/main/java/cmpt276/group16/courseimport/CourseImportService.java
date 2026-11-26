package cmpt276.group16.courseimport;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import cmpt276.group16.courseimport.SfuOutlineMapper.CourseListing;
import cmpt276.group16.courseimport.SfuOutlineMapper.OutlineData;
import cmpt276.group16.courseimport.SfuOutlineMapper.SectionListing;
import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseDesignation;
import cmpt276.group16.models.CourseDesignationRepository;
import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.CourseOfferingRepository;
import cmpt276.group16.models.CourseRefresh;
import cmpt276.group16.models.CourseRefreshRepository;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.Designation;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.Section;
import cmpt276.group16.models.SectionMeeting;
import cmpt276.group16.models.SectionRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.requisites.RequisiteImportService;
import cmpt276.group16.services.TermCreator;

// Imports one term's CMPT, ENSC, MACM and MATH undergraduate courses from the SFU Course Outlines API.
// It works in two steps so a failure never leaves half an import behind:
// 1. Fetch everything from the API into memory. 
//    Nothing is saved yet, so if the API fails here (SfuApiException) the database is exactly as it was.
// 2. Save it all in one transaction: courses and sections are updated in place, never duplicated.
// Every import is logged in course_refreshes, whether it worked or not
@Service 
public class CourseImportService {
    
    private static final Logger log = LoggerFactory.getLogger(CourseImportService.class);
 
    public static final List<String> DEPARTMENTS = List.of("CMPT", "ENSC", "MACM", "MATH");
 
    // Thrown when someone starts an import while another is still running
    public static class ImportAlreadyRunningException extends RuntimeException {
        public ImportAlreadyRunningException() {
            super("A course import is already running");
        }
    }

    // What an import that worked did, for the message after a manual refresh. The log keeps the same counts.
    // notes lists what was skipped and why. An import that fails throws instead of returning one
    public record ImportRun(int year, Season season, Instant finishedAt, int courses, int sections,
            int removedSections, int notOffered, List<String> notes) {
 
        // for example "2027 Spring"
        public String termName() {
            return year + " " + season.getShowName();
        }
    }

    // One course and its sections as fetched from the API, before anything is saved
    private record FetchedSection(SectionListing listing, List<SectionMeeting> meetings) {}
 
    private record FetchedCourse(String subject, String number, OutlineData details, List<FetchedSection> sections) {}

    private final SfuCourseOutlinesClient client;
    private final SfuOutlineMapper mapper;
    private final TermRepository termRepository;
    private final TermCreator termCreator;
    private final CourseRepository courseRepository;
    private final CourseDesignationRepository designationRepository;
    private final CourseOfferingRepository offeringRepository;
    private final SectionRepository sectionRepository;
    private final RequisiteImportService requisiteImportService;
    private final CourseRefreshRepository refreshRepository;
    private final TransactionTemplate transaction;
    private final Clock clock;

    // Only one import at a time: two at once would update the same rows
    private final AtomicBoolean running = new AtomicBoolean(false);

    // The log row of the import running now, so the log can tell it apart from one that was cut off.
    // In memory, so after a restart nothing reads as running
    private final AtomicReference<Long> runningRefreshId = new AtomicReference<>();
 
    public CourseImportService(SfuCourseOutlinesClient client, SfuOutlineMapper mapper,
            TermRepository termRepository, TermCreator termCreator, CourseRepository courseRepository,
            CourseDesignationRepository designationRepository, CourseOfferingRepository offeringRepository,
            SectionRepository sectionRepository, RequisiteImportService requisiteImportService,
            CourseRefreshRepository refreshRepository, PlatformTransactionManager transactionManager, Clock clock) {
        this.client = client;
        this.mapper = mapper;
        this.termRepository = termRepository;
        this.termCreator = termCreator;
        this.courseRepository = courseRepository;
        this.designationRepository = designationRepository;
        this.offeringRepository = offeringRepository;
        this.sectionRepository = sectionRepository;
        this.requisiteImportService = requisiteImportService;
        this.refreshRepository = refreshRepository;
        // Runs only the save step in a transaction, so the slow API calls before it don't hold a database connection,
        // and a failure while saving rolls back everything saved in this import
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    // The log on the Course refresh page, newest first
    public List<CourseRefresh> refreshLog() {
        return refreshRepository.findTop50ByOrderByStartedAtDescIdDesc();
    }

    public boolean isRunning() {
        return running.get();
    }

    // Whether this refresh from the log is the one running now.
    // One that hasn't finished and isn't running was cut off by the app stopping
    public boolean isRunning(CourseRefresh refresh) {
        Long id = runningRefreshId.get();
        return id != null && id.equals(refresh.getId());
    }

    // A refresh an IT manager started on the Course refresh page.
    // Throws SfuApiException if the API can't be used (nothing is saved then),
    // and ImportAlreadyRunningException if another import hasn't finished
    public ImportRun importTerm(int year, Season season) {
        return importTerm(year, season, false);
    }

    // The same, for a refresh the app starts on its own
    public ImportRun importTermAutomatically(int year, Season season) {
        return importTerm(year, season, true);
    }

    // Has to be called outside a transaction (the controller and the scheduler are), so the log row is saved
    // straight away and the save step gets its own transaction
    private ImportRun importTerm(int year, Season season, boolean automatic) {
        if (!running.compareAndSet(false, true)) {
            throw new ImportAlreadyRunningException();
        }
        CourseRefresh refresh = null;
        try {
            // Logged before anything else, so a refresh cut off by the app stopping still shows up
            refresh = refreshRepository.save(new CourseRefresh(year, season, automatic, Instant.now(clock)));
            runningRefreshId.set(refresh.getId());
            Long refreshId = refresh.getId();
            List<String> notes = new ArrayList<>();
            List<FetchedCourse> fetched = fetch(year, season, notes);
            Term term = findOrCreateTerm(year, season);
            ImportRun run = transaction.execute(status -> {
                ImportRun saved = save(term, fetched, notes);
                // In the same transaction as the courses, so the log can't say it finished
                // when the courses weren't saved, or the other way round
                refreshRepository.findById(refreshId).orElseThrow().finish(saved.finishedAt(), saved.courses(),
                        saved.sections(), saved.removedSections(), saved.notOffered(), saved.notes());
                return saved;
            });
            log.info("Imported {}: {} courses, {} sections, {} sections no longer offered, {} courses no longer offered",
                    run.termName(), run.courses(), run.sections(), run.removedSections(), run.notOffered());
            return run;
        } catch (SfuApiException e) {
            log.warn("Import of {} {} stopped: {}", year, season, e.getMessage());
            logFailure(refresh, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            // For example a database error while saving. The save transaction is rolled back,
            // so nothing was changed; the details go to the server log
            log.error("Import of {} {} failed", year, season, e);
            logFailure(refresh, "Saving the course data failed, so nothing was changed. Details are in the server log.");
            throw e;
        } finally {
            runningRefreshId.set(null);
            running.set(false);
        }
    }

    // refresh is null when saving the log row is what failed (the database being down, for example).
    // A failure here is only written to the server log, so it can't hide the import's own error
    private void logFailure(CourseRefresh refresh, String error) {
        if (refresh == null) {
            return;
        }
        try {
            refresh.fail(Instant.now(clock), error);
            refreshRepository.save(refresh);
        } catch (RuntimeException e) {
            log.error("Couldn't log the failed import of {}", refresh.getTermName(), e);
        }
    }
    
    /** 
     * Step 1: everything from the API, nothing saved
     */
 
    private List<FetchedCourse> fetch(int year, Season season, List<String> notes) {
        List<FetchedCourse> result = new ArrayList<>();
        boolean anyDepartment = false;
        for (String dept : DEPARTMENTS) {
            Optional<String> list = client.listCourses(year, season, dept);
            if (list.isEmpty()) {
                notes.add(dept + " has no courses this term");
                continue;
            }
            anyDepartment = true;
            for (CourseListing course : mapper.readCourses(list.get())) {
                // Graduate courses (500 and up) aren't part of any undergraduate plan
                if (course.number() != null && isUndergraduate(course.number())) {
                    fetchCourse(year, season, dept, course.number(), notes).ifPresent(result::add);
                }
            }
        }
        // Every department answering 404 means SFU hasn't published the term yet. Stopping here means the term
        // isn't added and its saved courses aren't all marked not offered
        if (!anyDepartment) {
            throw new SfuApiException("SFU hasn't published " + year + " " + season.getShowName() + " yet", null);
        }
        return result;
    }
 
    private Optional<FetchedCourse> fetchCourse(int year, Season season, String dept, String number, List<String> notes) {
        String name = dept + " " + number;
        Optional<String> list = client.listSections(year, season, dept, number);
        if (list.isEmpty()) {
            notes.add(name + " has no sections, skipped");
            return Optional.empty();
        }
        OutlineData details = null;
        boolean detailsFromPrimary = false;
        List<FetchedSection> sections = new ArrayList<>();
        for (SectionListing listing : mapper.readSections(list.get())) {
            if (listing.code() == null) {
                continue;
            }
            // A section whose outline is missing (404) is kept, just without meeting times
            List<SectionMeeting> meetings = List.of();
            Optional<String> outlineText = client.getOutline(year, season, dept, number, listing.code());
            if (outlineText.isPresent()) {
                OutlineData outline = mapper.readOutline(outlineText.get());
                meetings = outline.meetings();
                // Course details come from a lecture when there is one, otherwise from any section
                if (details == null || (listing.primary() && !detailsFromPrimary)) {
                    details = outline;
                    detailsFromPrimary = listing.primary();
                }
            }
            sections.add(new FetchedSection(listing, meetings));
        }
        if (details == null) {
            notes.add(name + " has no outline, skipped");
            return Optional.empty();
        }
        return Optional.of(new FetchedCourse(dept, number, details, sections));
    }
 
    // "105W" and "225" are undergraduate, "725" isn't
    static boolean isUndergraduate(String number) {
        char first = number.charAt(0);
        return first >= '1' && first <= '4';
    }
 
    // Returns the saved term for this year and season, adding it if it isn't saved yet.
    // TermCreator adds it in its own transaction; if someone else adds the same term at that moment,
    // the save fails and the term they added is looked up instead
    private Term findOrCreateTerm(int year, Season season) {
        return termRepository.findByTermYearAndSeason(year, season).orElseGet(() -> {
            try {
                return termCreator.create(year, season);
            } catch (DataIntegrityViolationException e) {
                return termRepository.findByTermYearAndSeason(year, season).orElseThrow(() -> e);
            }
        });
    }
 
    /**
     * Step 2: everything saved in one transaction
     */
 
    private ImportRun save(Term term, List<FetchedCourse> fetched, List<String> notes) {
        int courses = 0;
        int sections = 0;
        int removedSections = 0;
        Set<Long> offeringIds = new HashSet<>();
        for (FetchedCourse f : fetched) {
            Optional<Course> course = saveCourse(f, notes);
            if (course.isEmpty()) {
                continue;
            }
            saveDesignations(course.get(), f.details().designations());
            // Turns the prerequisite text just saved into rules. Text that doesn't parse marks the course
            // FAILED instead of stopping the import, so students are shown the Calendar text for it
            requisiteImportService.importPrerequisites(course.get());
            CourseOffering offering = saveOffering(course.get(), term);
            offeringIds.add(offering.getId());
            removedSections += saveSections(offering, f.sections());
            courses++;
            sections += f.sections().size();
        }
        int notOffered = markOthersNotOffered(term, offeringIds);
        return new ImportRun(term.getTermYear(), term.getSeason(), Instant.now(clock), courses, sections,
                removedSections, notOffered, List.copyOf(notes));
    }
 
    // Updates the course if it is already saved (for example a demo course), otherwise adds it.
    private Optional<Course> saveCourse(FetchedCourse f, List<String> notes) {
        OutlineData d = f.details();
        Optional<Course> existing = courseRepository.findByNormalizedSubjectAndNumber(f.subject(), f.number());
        if (existing.isPresent()) {
            Course course = existing.get();
            // A missing title or units keeps the saved one rather than blanking it
            if (d.title() != null) {
                course.setTitle(d.title());
            }
            if (d.units() != null) {
                course.setUnits(d.units());
            }
            course.setDescription(d.description());
            course.setPrereqText(d.prereqText());
            course.setCoreqText(d.coreqText());
            course.markFetched();
            return Optional.of(courseRepository.save(course));
        }
        // A new course needs a title and units, which the table requires
        if (d.title() == null || d.units() == null) {
            notes.add(f.subject() + " " + f.number() + " has no title or units, skipped");
            return Optional.empty();
        }
        return Optional.of(courseRepository.save(new Course(f.subject(), f.number(), d.title(), d.units(),
                d.description(), d.prereqText(), d.coreqText())));
    }
 
    // Makes the saved designations match the API: removes the ones it no longer lists, adds new ones
    private void saveDesignations(Course course, Set<Designation> wanted) {
        Set<Designation> missing = wanted.isEmpty() ? EnumSet.noneOf(Designation.class) : EnumSet.copyOf(wanted);
        for (CourseDesignation saved : designationRepository.findByCourseId(course.getId())) {
            if (!missing.remove(saved.getDesignation())) {
                designationRepository.delete(saved);
            }
        }
        for (Designation designation : missing) {
            designationRepository.save(new CourseDesignation(course, designation));
        }
    }
 
    private CourseOffering saveOffering(Course course, Term term) {
        CourseOffering offering = offeringRepository.findByCourseIdAndTermId(course.getId(), term.getId())
                .orElseGet(() -> new CourseOffering(course, term, true));
        offering.setOffered(true);
        offering.markFetched();
        return offeringRepository.save(offering);
    }
 
    // Updates or adds each section, then marks the ones SFU no longer lists as not offered.
    // They're kept rather than deleted, since a student's schedule can point at them.
    // Returns how many were newly marked
    private int saveSections(CourseOffering offering, List<FetchedSection> fetched) {
        Set<String> codes = new HashSet<>();
        for (FetchedSection f : fetched) {
            SectionListing listing = f.listing();
            Section section = sectionRepository
                    .findByOfferingIdAndNormalizedSectionCode(offering.getId(), listing.code())
                    .orElseGet(() -> new Section(offering, listing.code(), listing.type(), listing.primary(),
                            listing.associatedClass()));
            section.setSectionType(listing.type());
            section.setPrimarySection(listing.primary());
            section.setAssociatedClass(listing.associatedClass());
            section.replaceMeetings(f.meetings());
            // Listed again, for example a tutorial that was cancelled and then reopened
            section.setOffered(true);
            section.markFetched();
            codes.add(sectionRepository.save(section).getSectionCode());
        }
        int removed = 0;
        for (Section saved : sectionRepository.findByOfferingIdOrderBySectionCodeAsc(offering.getId())) {
            if (saved.isOffered() && !codes.contains(saved.getSectionCode())) {
                saved.setOffered(false);
                saved.markFetched();
                removed++;
            }
        }
        return removed;
    }
 
    // Courses of these departments that were offered this term before but weren't in this import
    // are marked not offered. The course itself is kept: plans, rules and transcripts point at it
    private int markOthersNotOffered(Term term, Set<Long> importedOfferingIds) {
        int count = 0;
        for (CourseOffering offering : offeringRepository
                .findByTermIdOrderByCourseSubjectAscCourseNumberAsc(term.getId())) {
            boolean ourDepartment = DEPARTMENTS.contains(offering.getCourse().getSubject());
            if (ourDepartment && offering.isOffered() && !importedOfferingIds.contains(offering.getId())) {
                offering.setOffered(false);
                offering.markFetched();
                count++;
            }
        }
        return count;
    }
}
