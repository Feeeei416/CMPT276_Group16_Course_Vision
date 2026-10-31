package cmpt276.group16.models;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TranscriptCourseRepository extends JpaRepository<TranscriptCourse, Long> {

	// in the order they were saved, which is the order they appear on the
	// transcript
	List<TranscriptCourse> findByStudentOrderByIdAsc(StudentProfile student);

	long countByStudent(StudentProfile student);

	void deleteByStudent(StudentProfile student);
}
