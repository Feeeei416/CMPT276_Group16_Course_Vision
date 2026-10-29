package cmpt276.group16.models;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    // Finds the account for a specific email 
    Optional<User> findByEmail(String email);

    // Check if the email already registered
    boolean existsByEmail(String email);

}

