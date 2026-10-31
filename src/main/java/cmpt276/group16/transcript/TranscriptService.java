package cmpt276.group16.transcript;

import java.util.List;

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

	// A newer transcript will replace the old courses instead of adding duplicates
	// This is one transaction, so a failed save leaves the old courses in place
	@Transactional
	public void replaceCourses(StudentProfile student, List<ParsedCourse> courses) {
		transcriptCourseRepository.deleteByStudent(student);
		transcriptCourseRepository.saveAll(courses.stream()
				.map(c -> new TranscriptCourse(student, c.term(), c.subject(), c.number(),
						c.title(), c.units(), c.grade()))
				.toList());
	}
}
