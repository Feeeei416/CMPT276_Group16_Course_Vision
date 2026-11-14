package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTermRepository extends JpaRepository<PlanTerm, Long> {

    // Not sorted; use PlanTerm.OLDEST_FIRST
    List<PlanTerm> findByPlan(Plan plan);

    // Empty when the term is in another student's plan, so changing the id in a request finds nothing
    Optional<PlanTerm> findByIdAndPlanStudent(Long id, StudentProfile student);

    boolean existsByPlanAndTerm(Plan plan, Term term);
}
