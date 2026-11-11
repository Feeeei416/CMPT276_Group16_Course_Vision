package cmpt276.group16.models;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // Finds a course by its code, for example "CMPT" + "225"
    // Case sensitive, so use findByNormalizedSubjectAndNumber unless the input is already uppercase
    Optional<Course> findBySubjectAndNumber(String subject, String number);

    // Same lookup, but accepts any case, for example "cmpt" + "105w"
    default Optional<Course> findByNormalizedSubjectAndNumber(String subject, String number) {
        return findBySubjectAndNumber(
                Course.normalizeSubjectOrNumber(subject),
                Course.normalizeSubjectOrNumber(number));
    }
}