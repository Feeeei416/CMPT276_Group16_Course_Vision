package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedCourseRepository extends JpaRepository<PlannedCourse, Long> {

    // Every course in a plan, across all its terms
    List<PlannedCourse> findByTermPlan(Plan plan);

    // Empty when the course is in another student's plan, so changing the id in a request finds nothing
    Optional<PlannedCourse> findByIdAndTermPlanStudent(Long id, StudentProfile student);

    // True if the course is already in any term of the plan
    boolean existsByTermPlanAndSubjectAndNumber(Plan plan, String subject, String number);

    // Used when a term is removed, so its courses go with it
    long deleteByTerm(PlanTerm term);
}
