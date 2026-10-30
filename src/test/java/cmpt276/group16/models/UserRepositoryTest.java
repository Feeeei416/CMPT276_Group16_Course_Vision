package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest 
class UserRepositoryTest {

    @Autowired 
    private UserRepository userRepository;

    @Autowired 
    private TestEntityManager entityManager;

    @Test 
    void findsUserByEmail() {
        userRepository.saveAndFlush(new User("MaddenWynans@example.com ", "hash", "Madden", Role.STUDENT));
        entityManager.clear();

        Optional<User> found = userRepository.findByEmail(User.normalizeEmail("MaDDeNWynans@example.com"));

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("maddenwynans@example.com");
        assertThat(found.get().getDisplayName()).isEqualTo("Madden");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void unknownEmailNotFound() {
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() {
        userRepository.saveAndFlush(new User("maddenwynans@example.com", "hash", "Madden", Role.STUDENT));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("MaddenWynans@example.com", "hash2", "Madden1", Role.STUDENT)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Runs once per role: each one must save as its name and load back unchanged
    @ParameterizedTest
    @EnumSource(Role.class)
    void savesAndLoadsEveryRole(Role role) {
        User saved = userRepository.saveAndFlush(new User("someone@example.com", "hash", "Someone", role));
        entityManager.clear();

        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getRole()).isEqualTo(role);

        Object storedRole = entityManager.getEntityManager()
                .createNativeQuery("select role from users where id = ?1")
                .setParameter(1, saved.getId())
                .getSingleResult();
        assertThat(storedRole.toString()).isEqualTo(role.name());
    }

    @Test
    void setEmailStoresItLowercase() {
        User user = new User("madden@example.com", "hash", "Madden", Role.STUDENT);

        user.setEmail("  New.Email@Example.com ");

        assertThat(user.getEmail()).isEqualTo("new.email@example.com");
    }

    @Test
    void normalizedLookupsIgnoreCaseAndSpaces() {
        userRepository.saveAndFlush(new User("madden@example.com", "hash", "Madden", Role.STUDENT));

        assertThat(userRepository.findByNormalizedEmail("  MADDEN@Example.com ")).isPresent();
        assertThat(userRepository.existsByNormalizedEmail(" Madden@EXAMPLE.com")).isTrue();
        assertThat(userRepository.existsByNormalizedEmail("someone.else@example.com")).isFalse();
    }
}
