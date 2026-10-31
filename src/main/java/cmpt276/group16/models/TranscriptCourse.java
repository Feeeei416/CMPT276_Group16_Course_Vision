package cmpt276.group16.models;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "transcript_courses")
public class TranscriptCourse {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Many courses belong to one student
	@ManyToOne
	@JoinColumn(name = "student_id", nullable = false)
	private StudentProfile student;

	// for example "2024 Fall"
	@Column(nullable = false)
	private String term;

	// for example "CMPT"
	@Column(nullable = false)
	private String subject;

	// for example "225" or "105W"
	@Column(name = "course_number", nullable = false)
	private String number;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, precision = 4, scale = 2)
	private BigDecimal units;

	// This is Null while the course is in progress
	private String grade;

	protected TranscriptCourse() {
	}

	public TranscriptCourse(StudentProfile student, String term, String subject, String number,
			String title, BigDecimal units, String grade) {
		this.student = student;
		this.term = term;
		this.subject = subject;
		this.number = number;
		this.title = title;
		this.units = units;
		this.grade = grade;
	}

	public Long getId() {
		return id;
	}

	public StudentProfile getStudent() {
		return student;
	}

	public String getTerm() {
		return term;
	}

	public String getSubject() {
		return subject;
	}

	public String getNumber() {
		return number;
	}

	public String getTitle() {
		return title;
	}

	public BigDecimal getUnits() {
		return units;
	}

	public String getGrade() {
		return grade;
	}

	public boolean isInProgress() {
		return grade == null;
	}
}
