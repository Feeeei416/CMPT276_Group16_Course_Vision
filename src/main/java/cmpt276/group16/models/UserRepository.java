package cmpt276.group16.models;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // Finds the account for a specific email 
    Optional<User> findByEmail(String email);

    // Check if the email already registered
    boolean existsByEmail(String email);

    // Same as findByEmail, but normalizes the email first so callers can pass it as typed
    default Optional<User> findByNormalizedEmail(String email) {
        return findByEmail(User.normalizeEmail(email));
    }

    // Same as existsByEmail, but normalizes the email first
    default boolean existsByNormalizedEmail(String email) {
        return existsByEmail(User.normalizeEmail(email));
    }

}

