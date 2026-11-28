package cmpt276.group16.models;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // Finds a course by its code, for example "CMPT" + "225"
    // Case sensitive, so use findByNormalizedSubjectAndNumber unless the input is already uppercase
    Optional<Course> findBySubjectAndNumber(String subject, String number);

    // Every course in any of the subjects with any of the numbers, in one query. That can include pairs that weren't
    // asked for, like MATH 225 when asking for CMPT 225 and MATH 150, so the caller matches the codes it wants.
    // Uppercase input only. Used by the Graduation Report (#69)
    List<Course> findBySubjectInAndNumberIn(Collection<String> subjects, Collection<String> numbers);

    // Same lookup, but accepts any case, for example "cmpt" + "105w"
    default Optional<Course> findByNormalizedSubjectAndNumber(String subject, String number) {
        return findBySubjectAndNumber(
                Course.normalizeSubjectOrNumber(subject),
                Course.normalizeSubjectOrNumber(number));
    }
}