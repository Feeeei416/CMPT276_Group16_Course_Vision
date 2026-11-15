package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    // The plan a student works from; empty until they first open My plan
    Optional<Plan> findByStudentAndMainTrue(StudentProfile student);

    // Every plan the student has, in the order they were made
    List<Plan> findByStudentOrderByIdAsc(StudentProfile student);

    // Empty when the plan is another student's, so changing the id in a request finds nothing
    Optional<Plan> findByIdAndStudent(Long id, StudentProfile student);

    // Names are checked ignoring case, so "Co-op" and "co-op" can't both exist
    boolean existsByStudentAndNameIgnoreCase(StudentProfile student, String name);
}
