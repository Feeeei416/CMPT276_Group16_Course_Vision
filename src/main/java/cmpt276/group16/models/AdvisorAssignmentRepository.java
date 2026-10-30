package cmpt276.group16.models;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisorAssignmentRepository extends JpaRepository<AdvisorAssignment, Long> {

    // True if this advisor supports this student; #9 checks it before showing a student's record
    boolean existsByAdvisorIdAndStudentUserId(Long advisorId, Long studentId);
}
