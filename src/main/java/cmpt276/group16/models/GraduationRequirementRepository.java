package cmpt276.group16.models;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GraduationRequirementRepository extends JpaRepository<GraduationRequirement, Long> {

    // A set's requirements in the order they're shown. Positions can tie while they're being reordered,
    // so the id decides between them.
    // The designations come in the same query, instead of one more query for each requirement
    @EntityGraph(attributePaths = "designations")
    List<GraduationRequirement> findByRequirementSetOrderByPositionAscIdAsc(RequirementSet requirementSet);
}
