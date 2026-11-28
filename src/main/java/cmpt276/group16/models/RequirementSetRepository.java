package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementSetRepository extends JpaRepository<RequirementSet, Long> {

    // The set a student follows; empty if their program and year have no requirements yet
    Optional<RequirementSet> findByProgramAndEntryYear(Program program, int entryYear);

    // Lets the loader skip sets that are already saved, so edits made in the app are kept
    boolean existsByProgramAndEntryYear(Program program, int entryYear);

    // For a list of every set, grouped by program
    List<RequirementSet> findAllByOrderByProgramAscEntryYearAsc();
}
