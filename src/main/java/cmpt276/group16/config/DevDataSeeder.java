package cmpt276.group16.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import cmpt276.group16.models.*;
@Configuration 
public class DevDataSeeder {
    @Bean 
    CommandLineRunner seedUsers (UserRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (!repo.existsByNormalizedEmail("student@sfu.ca")){
                User newStudent = new User("student@sfu.ca", encoder.encode("test123"), "Test Student", Role.STUDENT);
                repo.save(newStudent);
            }
            if (!repo.existsByNormalizedEmail("advisor@sfu.ca")){
                User newAdvisor = new User("advisor@sfu.ca", encoder.encode("test456"), "Test Advisor", Role.ADVISOR);
                repo.save(newAdvisor);
            }
            if (!repo.existsByNormalizedEmail("it@sfu.ca")){
                User newIT = new User("it@sfu.ca", encoder.encode("test789"), "Test IT Manager", Role.IT_MANAGER);
                repo.save(newIT);
            }
        };
    }
}
