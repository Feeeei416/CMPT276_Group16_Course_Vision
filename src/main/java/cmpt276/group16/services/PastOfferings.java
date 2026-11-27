package cmpt276.group16.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.CourseOffering;
import cmpt276.group16.models.CourseOfferingRepository;
import cmpt276.group16.models.YearAndSeason;

// Which recent terms a course ran in, from the past terms' offerings (#57).
// "Recent" is the 6 terms before this one, about two years, enough to tell a course that runs every term from one
// that runs once a year
@Service 
public class PastOfferings {
    public static final int PAST_TERMS = 6;

    private final CourseOfferingRepository offeringRepository;

    public PastOfferings(CourseOfferingRepository offeringRepository) {
        this.offeringRepository = offeringRepository;
    }

    // The 6 terms before this one, oldest first, for example 2024 Fall to 2026 Summer in November 2026
    public static List<YearAndSeason> recentTerms(LocalDate today) {
        YearAndSeason thisTerm = YearAndSeason.of(today);
        List<YearAndSeason> options = new ArrayList<>();
        for (int i = PAST_TERMS; i > 0; i--) {
            YearAndSeason term = thisTerm.plus(-i);
            options.add(term);
        }
        return options;
    }

    // Which of recentTerms(today) the course was offered in, oldest first
    @Transactional(readOnly = true)
    public List<YearAndSeason> termsOffered(Long courseId, LocalDate today) {
        // Every term the course ran in, in no particular order
        Set<YearAndSeason> ran = new HashSet<>();
        for (CourseOffering offering : offeringRepository.findByCourseIdAndOfferedTrue(courseId)) {
            ran.add(new YearAndSeason(offering.getTerm().getTermYear(), offering.getTerm().getSeason()));
        }

        // Going through recentTerms keeps them oldest first
        List<YearAndSeason> offered = new ArrayList<>();
        for (YearAndSeason term: recentTerms(today)) {
            if (ran.contains(term)) {
                offered.add(term);
            }
        }
        return offered;
    }
}
