package cmpt276.group16.models;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    // The plan a student works from; empty until they first open My plan
    Optional<Plan> findByStudentAndMainTrue(StudentProfile student);
}
