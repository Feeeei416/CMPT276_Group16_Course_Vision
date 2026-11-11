package cmpt276.group16.transcript;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;

// Every change to a student's courses goes through here. Each one first locks the student, because adding and
// editing check for a duplicate and then save: two requests at once (a double click) would both pass the check
// and save two copies. With the lock, the second request waits and then sees the first one's course
@Service
public class TranscriptService {

    private final TranscriptCourseRepository transcriptCourseRepository;
    private final StudentProfileRepository studentProfileRepository;

    public TranscriptService(TranscriptCourseRepository transcriptCourseRepository,
            StudentProfileRepository studentProfileRepository) {
        this.transcriptCourseRepository = transcriptCourseRepository;
        this.studentProfileRepository = studentProfileRepository;
    }

    // A newer transcript replaces the courses from the last one instead of adding duplicates
    // Courses added or edited by hand are kept, and the transcript's copy of one (same term, subject and
    // number) is skipped, so the student's fix isn't undone or doubled
    // This is one transaction, so a failed save leaves the old courses in place
    @Transactional
    public void replaceCourses(StudentProfile student, List<ParsedCourse> courses) {
        lock(student);
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

    // False (and nothing saved) when the student already has this course in that term
    @Transactional
    public boolean addByHand(StudentProfile student, CourseInput course) {
        lock(student);
        if (transcriptCourseRepository.existsByStudentAndTermAndSubjectAndNumber(student, course.term(),
                course.subject(), course.number())) {
            return false;
        }
        transcriptCourseRepository.save(new TranscriptCourse(student, course.term(), course.subject(),
                course.number(), course.title(), course.units(), course.grade(), TranscriptCourse.Source.MANUAL));
        return true;
    }

    // False (and nothing changed) when the edit would make it a copy of another course in the record
    @Transactional
    public boolean editByHand(TranscriptCourse course, CourseInput edited) {
        lock(course.getStudent());
        if (transcriptCourseRepository.existsByStudentAndTermAndSubjectAndNumberAndIdNot(course.getStudent(),
                edited.term(), edited.subject(), edited.number(), course.getId())) {
            return false;
        }
        course.editByHand(edited.term(), edited.subject(), edited.number(), edited.title(), edited.units(),
                edited.grade());
        transcriptCourseRepository.save(course);
        return true;
    }

    private void lock(StudentProfile student) {
        studentProfileRepository.findLockedByUserId(student.getUserId());
    }

    // The same course taken in the same term. A record gets equals and hashCode from its fields,
    // so it works as a Set key
    private record CourseKey(String term, String subject, String number) {
    }
}
