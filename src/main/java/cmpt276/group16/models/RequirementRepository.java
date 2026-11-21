package cmpt276.group16.models;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {

    // A set's requirements in the order they're shown
    List<Requirement> findByRequirementSetOrderByPositionAsc(RequirementSet requirementSet);
}
