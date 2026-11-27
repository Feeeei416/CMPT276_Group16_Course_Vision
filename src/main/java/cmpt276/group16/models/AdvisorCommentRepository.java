package cmpt276.group16.models;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisorCommentRepository extends JpaRepository<AdvisorComment, Long> {

    // One student's comments, newest first. Two saved in the same instant keep the order they were saved in
    List<AdvisorComment> findByStudentOrderByCreatedAtDescIdDesc(StudentProfile student);

    // Deletes the comment only if it's on this student's report and this advisor wrote it.
    // Returns how many were deleted (0 or 1)
    long deleteByIdAndStudentAndAdvisor(Long id, StudentProfile student, User advisor);
}
