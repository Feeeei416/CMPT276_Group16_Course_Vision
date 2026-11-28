package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NextSemesterScheduleRepository extends JpaRepository<NextSemesterSchedule, Long>{
    Optional<NextSemesterSchedule> findByStudentAndTermAndMainTrue(StudentProfile student, Term term);

    // Every schedule the student has for one term, in the order they were made
    List<NextSemesterSchedule> findByStudentAndTermOrderByIdAsc(StudentProfile student, Term term);

    // Empty when the schedule is another student's, or for a term that isn't the one asked for,
    // so changing the id in a request won't find anything
    Optional<NextSemesterSchedule> findByIdAndStudentAndTerm(Long id, StudentProfile student, Term term);

    // Names are checked ignoring case, so "Morning labs" and "morning labs" can not both exist in one term
    boolean existsByStudentAndTermAndNameIgnoreCase(StudentProfile student, Term term, String name);
}
