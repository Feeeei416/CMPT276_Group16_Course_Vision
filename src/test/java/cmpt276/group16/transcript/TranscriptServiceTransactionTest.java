package cmpt276.group16.transcript;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// @DataJpaTest normally runs each test inside a transaction, which hides a missing @Transactional
// on TranscriptService. NOT_SUPPORTED turns that off, so this fails if the annotation is removed
@DataJpaTest
@Import(TranscriptService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TranscriptServiceTransactionTest {

	@Autowired
	private TranscriptService transcriptService;

	@Autowired
	private TranscriptCourseRepository transcriptCourseRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private StudentProfileRepository studentProfileRepository;

	private StudentProfile student;

	private static final ParsedCourse COURSE = new ParsedCourse("2025 Fall", "CMPT", "120",
			"Intro.Cmpt.Sci/Programming I", new BigDecimal("3.00"), "A-");

	// Nothing is rolled back without a test transaction, so clean up for the other
	// tests
	@AfterEach
	void deleteStudent() {
		if (student != null) {
			transcriptCourseRepository.deleteAll(transcriptCourseRepository.findByStudentOrderByIdAsc(student));
			studentProfileRepository.delete(student);
			userRepository.delete(student.getUser());
		}
	}

	// The second call has old courses to delete, which is the part that needs a
	// transaction
	@Test
	void replacingWorksWithoutATestTransaction() {
		User user = userRepository.save(new User("no.transaction@example.com", "hash", "Student", Role.STUDENT));
		student = studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));

		transcriptService.replaceCourses(student, List.of(COURSE));
		transcriptService.replaceCourses(student, List.of(COURSE));

		assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(student))
				.extracting(TranscriptCourse::getNumber).containsExactly("120");
	}
}
