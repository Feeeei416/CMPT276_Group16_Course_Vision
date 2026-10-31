package cmpt276.group16.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cmpt276.group16.models.UserRepository;

// Starts the whole app with a demo password set, the same as setting DEMO_PASSWORD
@SpringBootTest(properties = "app.demo-password=test-password")
class DataSeederStartupTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void demoAccountsExistAfterStartup() {
        assertThat(userRepository.count()).isEqualTo(4);
        assertThat(userRepository.existsByEmail("advisor@example.com")).isTrue();
    }
}
