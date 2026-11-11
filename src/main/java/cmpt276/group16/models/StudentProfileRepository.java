package cmpt276.group16.models;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    // Locks the student's row until the transaction ends (SELECT ... FOR UPDATE). Another transaction that
    // asks for the same lock waits, so changes to one student's courses happen one at a time
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StudentProfile> findLockedByUserId(Long userId);
}
