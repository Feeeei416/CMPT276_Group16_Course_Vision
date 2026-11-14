package cmpt276.group16.services;

import java.time.LocalDate;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import cmpt276.group16.models.*;

@Service
public class NextSemesterScheduleService {

    private final NextSemesterScheduleRepository scheduleRepository;
    private final ScheduledCourseRepository scheduledCourseRepository;
    private final SectionRepository sectionRepository;
    private final TermRepository termRepository;
    private final Clock clock;

    public NextSemesterScheduleService(NextSemesterScheduleRepository scheduleRepository,
            ScheduledCourseRepository scheduledCourseRepository,
            SectionRepository sectionRepository, TermRepository termRepository, Clock clock) {
        this.scheduleRepository = scheduleRepository;
        this.scheduledCourseRepository = scheduledCourseRepository;
        this.sectionRepository = sectionRepository;
        this.termRepository = termRepository;
        this.clock = clock;
    }

    public Optional<Term> nextSemesterTerm() {

        LocalDate today = LocalDate.now(clock);
        int month = today.getMonthValue();
        int year = today.getYear();

        if (month <= 4) {
            return termRepository.findByTermYearAndSeason(year, Season.SUMMER);
        }
        if (month <= 8) {
            return termRepository.findByTermYearAndSeason(year, Season.FALL);
        }
        return termRepository.findByTermYearAndSeason(year + 1, Season.SPRING);

    }
}
