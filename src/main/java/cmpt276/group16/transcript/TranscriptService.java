package cmpt276.group16.transcript;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;

@Service
public class TranscriptService {

    private final TranscriptCourseRepository transcriptCourseRepository;

    public TranscriptService(TranscriptCourseRepository transcriptCourseRepository) {
        this.transcriptCourseRepository = transcriptCourseRepository;
    }

    // A newer transcript replaces the courses from the last one instead of adding duplicates
    // Courses added or edited by hand are kept, and the transcript's copy of one (same term, subject and
    // number) is skipped, so the student's fix isn't undone or doubled
    // This is one transaction, so a failed save leaves the old courses in place
    @Transactional
    public void replaceCourses(StudentProfile student, List<ParsedCourse> courses) {
        transcriptCourseRepository.deleteByStudentAndSource(student, TranscriptCourse.Source.TRANSCRIPT);
        Set<CourseKey> manualCourses = transcriptCourseRepository
                .findByStudentAndSource(student, TranscriptCourse.Source.MANUAL).stream()
                .map(c -> new CourseKey(c.getTerm(), c.getSubject(), c.getNumber()))
                .collect(Collectors.toSet());
        transcriptCourseRepository.saveAll(courses.stream()
                .filter(c -> !manualCourses.contains(new CourseKey(c.term(), c.subject(), c.number())))
                .map(c -> new TranscriptCourse(student, c.term(), c.subject(), c.number(),
                        c.title(), c.units(), c.grade()))
                .toList());
    }

    // The same course taken in the same term. A record gets equals and hashCode from its fields,
    // so it works as a Set key
    private record CourseKey(String term, String subject, String number) {
    }
}
