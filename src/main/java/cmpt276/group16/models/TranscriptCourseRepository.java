package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptCourseRepository extends JpaRepository<TranscriptCourse, Long> {

    // In the order they were saved, which is the order they appear on the transcript
    List<TranscriptCourse> findByStudentOrderByIdAsc(StudentProfile student);

    // Empty when the course belongs to another student, so changing the id in a request finds nothing
    Optional<TranscriptCourse> findByIdAndStudent(Long id, StudentProfile student);

    long countByStudentAndSource(StudentProfile student, TranscriptCourse.Source source);

    long deleteByStudentAndSource(StudentProfile student, TranscriptCourse.Source source);

    List<TranscriptCourse> findByStudentAndSource(StudentProfile student, TranscriptCourse.Source source);
}
