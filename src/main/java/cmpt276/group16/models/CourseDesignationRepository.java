package cmpt276.group16.models;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// The second type is the composite id class, not Long, because the primary key has two columns
public interface CourseDesignationRepository extends JpaRepository<CourseDesignation, CourseDesignationId> {
    
    // Every WQB designation one course counts for
    List<CourseDesignation> findByCourseId(Long courseId);

    // True if this course counts for this designation, for example whether CMPT 105W counts as W.
    // "IdDesignation" because designation lives inside the id, not on the entity itself
    boolean existsByCourseIdAndIdDesignation(Long courseId, Designation designation);
}
