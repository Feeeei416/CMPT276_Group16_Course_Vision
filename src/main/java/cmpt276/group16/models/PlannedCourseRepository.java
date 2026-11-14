package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlannedCourseRepository extends JpaRepository<PlannedCourse, Long> {

    // Every course in a plan, across all its terms
    List<PlannedCourse> findByTermPlan(Plan plan);

    // Empty when the course is in another student's plan, so changing the id in a request finds nothing
    Optional<PlannedCourse> findByIdAndTermPlanStudent(Long id, StudentProfile student);

    // The course if it's already in any term of the plan, so the error can say which term
    Optional<PlannedCourse> findByTermPlanAndCourse(Plan plan, Course course);

    // One term's courses in the order they were added
    List<PlannedCourse> findByTermOrderByIdAsc(PlanTerm term);

    // Used when a term is removed, so its courses go with it
    long deleteByTerm(PlanTerm term);
}
