package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequisiteRuleRepository extends JpaRepository<RequisiteRule, Long> {

    // The root of one course's prerequisite or corequisite tree; empty if the course has none
    Optional<RequisiteRule> findByCourseIdAndRuleKindAndParentIsNull(Long courseId, RuleKind ruleKind);

    // The children of one AND or OR group, in the order they were saved
    List<RequisiteRule> findByParentIdOrderByIdAsc(Long parentId);

    // Every node of one course's tree in a single query, so the tree can be built without one query per node
    List<RequisiteRule> findByCourseIdAndRuleKindOrderByIdAsc(Long courseId, RuleKind ruleKind);

    // Every rule that requires this course, for example to see which courses CMPT 125 unlocks
    List<RequisiteRule> findByRequiredCourseId(Long courseId);
}