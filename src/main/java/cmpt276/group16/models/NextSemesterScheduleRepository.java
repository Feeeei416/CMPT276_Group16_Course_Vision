package cmpt276.group16.models;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NextSemesterScheduleRepository extends JpaRepository<NextSemesterSchedule, Long>{
    Optional<NextSemesterSchedule> findByStudentAndTermAndMainTrue(StudentProfile student, Term term);
}
