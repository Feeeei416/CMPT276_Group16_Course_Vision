package cmpt276.group16.models;

import java.util.*;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectionRepository extends JpaRepository<Section, Long>{

    // Finds one section of an offering, for example D100 of CMPT 225 in 2027 Spring.
    // Used when re-fetching, to update the existing row instead of adding a duplicate.
    // Case sensitive, so use findByOfferingIdAndNormalizedSectionCode unless the code is already uppercase
    Optional<Section> findByOfferingIdAndSectionCode(Long offeringId, String sectionCode);

    // Same lookup, but accepts any case, for example "d100"
    default Optional<Section> findByOfferingIdAndNormalizedSectionCode(Long offeringId, String sectionCode) {
        return findByOfferingIdAndSectionCode(offeringId, Course.normalizeSubjectOrNumber(sectionCode));
    }

    // Every section of one offering, sorted by code (D100, D101, ...), for example to show a course's
    // sections in a schedule, or to find sections SFU has removed since the last fetch.
    // The EntityGraph loads the meetings in the same query, instead of one extra query per section
    @EntityGraph(attributePaths = "meetings")
    List<Section> findByOfferingIdOrderBySectionCodeAsc(Long offeringId);
}
