package cmpt276.group16.models;

import java.math.BigDecimal;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;

@Entity
@Table(name = "transcript_courses")
public class TranscriptCourse {

    // Where a row came from. Confirming a new transcript only replaces TRANSCRIPT rows
    public enum Source {
        TRANSCRIPT,
        // added or edited by the student
        MANUAL
    }

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

    // This is Null while the course is in progress, and Grades.TRANSFER for transfer credit
    private String grade;

    // The default fills in rows saved before this column existed, which all came from transcripts
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'TRANSCRIPT'")
    @Column(nullable = false)
    private Source source;

    protected TranscriptCourse() {
    }

    // A course read from a transcript
    public TranscriptCourse(StudentProfile student, String term, String subject, String number,
            String title, BigDecimal units, String grade) {
        this(student, term, subject, number, title, units, grade, Source.TRANSCRIPT);
    }

    public TranscriptCourse(StudentProfile student, String term, String subject, String number,
            String title, BigDecimal units, String grade, Source source) {
        this.student = student;
        this.term = term;
        this.subject = subject;
        this.number = number;
        this.title = title;
        this.units = units;
        this.grade = grade;
        this.source = source;
    }

    // An edited course becomes MANUAL, so a newer transcript won't undo the student's fix
    public void editByHand(String term, String subject, String number, String title, BigDecimal units,
            String grade) {
        this.term = term;
        this.subject = subject;
        this.number = number;
        this.title = title;
        this.units = units;
        this.grade = grade;
        this.source = Source.MANUAL;
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

    public Source getSource() {
        return source;
    }

    public boolean isInProgress() {
        return grade == null;
    }

    public boolean isTransferCredit() {
        return Grades.TRANSFER.equals(grade);
    }
}
