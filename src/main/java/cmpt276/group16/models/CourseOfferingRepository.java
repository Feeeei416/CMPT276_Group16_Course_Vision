package cmpt276.group16.models;

import java.util.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    // True if this course already has an offering row for this term
    boolean existsByCourseIdAndTermId(Long courseId, Long termId);

    // Used when re-fetching, to update the existing row instead of adding a duplicate
    Optional<CourseOffering> findByCourseIdAndTermId(Long courseId, Long termId);

    // Every offering in one term, sorted by course code, for example to list what runs in 2025 Spring
    List<CourseOffering> findByTermIdOrderByCourseSubjectAscCourseNumberAsc(Long termId);
}