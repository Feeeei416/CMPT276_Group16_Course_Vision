package cmpt276.group16.models;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisorCommentRepository extends JpaRepository<AdvisorComment, Long> {

    // One student's comments, newest first. Two saved in the same instant keep the order they were saved in
    List<AdvisorComment> findByStudentOrderByCreatedAtDescIdDesc(StudentProfile student);
}
