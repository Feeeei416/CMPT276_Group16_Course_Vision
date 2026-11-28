package cmpt276.group16.models;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementCourseRepository extends JpaRepository<RequirementCourse, Long> {

    // The courses of one requirement, in the order they were saved
    List<RequirementCourse> findByRequirementOrderByIdAsc(GraduationRequirement requirement);

    // Every course of a whole set in one query, so a report doesn't need a query per requirement
    List<RequirementCourse> findByRequirementRequirementSetOrderByIdAsc(RequirementSet requirementSet);
}
