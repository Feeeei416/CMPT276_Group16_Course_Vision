package cmpt276.group16.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.*;

// Starts the whole app with a real database; @Transactional rolls back each test's changes
@SpringBootTest 
@Transactional 
public class RegistrationServiceTest {
    @Autowired  
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // A form that passes every rule; each test changes one field to break one rule
    private Map<String, String> validForm() {
        Map<String, String> form = new HashMap<>();
        form.put("email", "newstudent@test.com");
        form.put("displayName", "New Student");
        form.put("password", "password123");
        form.put("confirmPassword", "password123");
        form.put("program", "COMPUTING_SCIENCE");
        form.put("cohortYear", "2024");
        return form;
    }

    // -- Valid input --
    @Test
    void validForm_hasNoErrors() {
        List<String> errors = registrationService.validate(validForm());
        assertTrue(errors.isEmpty());
    }

    // -- Empty form --
    @Test
    void emptyForm_reportsEveryRequiredField() {
        List<String> errors = registrationService.validate(new HashMap<>());

        assertTrue(errors.contains("Email is required."));
        assertTrue(errors.contains("Display Name is required."));
        assertTrue(errors.contains("Password is required."));
        assertTrue(errors.contains("Choose a program."));
        assertTrue(errors.contains("Choose an entry year."));
    }

    // -- Email --
    @Test
    void emailWithoutAt_isRejected() {
        Map<String, String> form = validForm();
        form.put("email", "newstudent.test.com");

        List<String> errors = registrationService.validate(form);

        assertTrue(errors.contains("Email format is not valid."));
    }

    @Test
    void emailWithoutDotAfterAt_isRejected() {
        Map<String, String> form = validForm();
        form.put("email", "newstudent@test");

        List<String> errors = registrationService.validate(form);

        assertTrue(errors.contains("Email format is not valid."));
    }

    @Test
    void existingEmail_isRejectedIgnoringCase() {
        userRepository.save(new User("newstudent@test.com", "hash", "Someone", Role.STUDENT));
        Map<String, String> form = validForm();
        form.put("email", "  NewStudent@Test.com ");

        List<String> errors = registrationService.validate(form);

        assertTrue(errors.contains("An account with this email already exists."));
    }

    // -- Display name --
    @Test
    void displayNameOver255Characters_isRejected() {
        Map<String, String> form = validForm();
        form.put("displayName", "a".repeat(256));

        List<String> errors = registrationService.validate(form);

        assertTrue(errors.contains("Display Name must be at most 255 characters."));
    }

    // -- Password --
    @Test
    void mismatchedPasswords_areRejected() {
        Map<String, String> form = validForm();
        form.put("confirmPassword", "different123");

        List<String> errors = registrationService.validate(form);

        assertTrue(errors.contains("Passwords do not match."));
    }

    @Test
    void passwordShorterThan8_isRejected() {
        Map<String, String> form = validForm();
        // 7 characters
        form.put("password", "short12");          
        form.put("confirmPassword", "short12");

        List<String> errors = registrationService.validate(form);

        assertTrue(errors.contains("Password must be 8 to 72 characters."));
    }

    @Test
    void passwordLongerThan72_isRejected() {
        Map<String, String> form = validForm();
        String longPassword = "a".repeat(73);
        form.put("password", longPassword);
        form.put("confirmPassword", longPassword);

        List<String> errors = registrationService.validate(form);

        assertTrue(errors.contains("Password must be 8 to 72 characters."));
    }

    @Test
    void passwordOfExactly8And72_isAccepted() {
        for (int length : new int[] {8, 72}) {
            Map<String, String> form = validForm();
            String password = "a".repeat(length);
            form.put("password", password);
            form.put("confirmPassword", password);

            assertTrue(registrationService.validate(form).isEmpty(), "length " + length);
        }
    }

    // -- Program and entry year --
    @Test
    void unknownProgram_isRejected() {
        Map<String, String> form = validForm();
        form.put("program", "ARTS");

        List<String> errors = registrationService.validate(form);

        assertTrue(errors.contains("Choose a program."));
    }

    @Test
    void unsupportedOrNonNumericYear_isRejected() {
        for (String year : new String[] {"2022", "abc"}) {
            Map<String, String> form = validForm();
            form.put("cohortYear", year);

            assertTrue(registrationService.validate(form).contains("Choose an entry year."), year);
        }
    }

    @Test
    void parseMethods_ignoreSurroundingSpaces() {
        assertEquals(Program.COMPUTER_ENGINEERING, RegistrationService.parseProgram(" COMPUTER_ENGINEERING "));
        assertEquals(2023, RegistrationService.parseCohortYear(" 2023 "));
    }

    // -- Saving --
    @Test
    void createUserAndStudentProfile_savesStudentWithHashedPasswordAndProfile() {
        User user = registrationService.createUserAndStudentProfile(
                "NewStudent@Test.com", "  New Student ", "password123", Program.COMPUTER_ENGINEERING, 2023);

        // Load back from the database to check what was really stored
        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("newstudent@test.com", saved.getEmail());
        assertEquals("New Student", saved.getDisplayName());
        assertEquals(Role.STUDENT, saved.getRole());
        assertNotEquals("password123", saved.getPasswordHash());
        assertTrue(passwordEncoder.matches("password123", saved.getPasswordHash()));

        StudentProfile profile = studentProfileRepository.findById(user.getId()).orElseThrow();
        assertEquals(Program.COMPUTER_ENGINEERING, profile.getProgram());
        assertEquals(2023, profile.getCohortYear());
    }

}
