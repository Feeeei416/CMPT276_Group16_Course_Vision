package cmpt276.group16.services;

import cmpt276.group16.models.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service 
public class RegistrationService {

    // Entry years we support (the 2023 and 2024 cohorts);
    public static final List<Integer> COHORT_YEARS = List.of(2023, 2024);

    // At least 8 characters; BCrypt only uses the first 72 bytes, so longer passwords are rejected
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_BYTES = 72;

    // Simple check: something@something.something with no spaces
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository,
                               StudentProfileRepository studentProfileRepository,
                               PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
    }


    /**
     * Checks the registration form and returns every problem found.
     * An empty list means the form is valid and can be passed to createUserAndStudentProfile.
     */
    public List<String> validate(Map<String, String> form) {
        List<String> errors = new ArrayList<>();

        // Email: required, valid format, not already registered
        String email = clean(form, "email");
        if (email.isEmpty()){
            errors.add("Email is required.");
        }
        else if (!EMAIL_PATTERN.matcher(email).matches()){
            errors.add("Email format is not valid.");
        }
        else if (userRepository.existsByNormalizedEmail(email)) {
            errors.add("An account with this email already exists.");
        }


        // Display name: required and limited length 255 char
        String displayName = clean(form, "displayName");
        if (displayName.isEmpty()){
            errors.add("Display Name is required.");
        }
        else if (displayName.length() > 255) {
            errors.add("Display Name must be at most 255 characters.");
        }

        // Passwords are not trimmed: spaces can be part of a password
        String password = raw(form, "password");
        String confirmPassword = raw(form, "confirmPassword");

        // Password: required, length limits, and both entries must match
        if (password.isEmpty()) {
            errors.add("Password is required.");
        } else {
            if (password.length() < MIN_PASSWORD_LENGTH
                    || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
                errors.add("Password must be 8 to 72 characters.");
            }
            if (!password.equals(confirmPassword)) {
                errors.add("Passwords do not match.");
            }
        }

        // Program and entry year must be one of the listed options
        if (parseProgram(clean(form, "program")) == null) {
            errors.add("Choose a program.");
        }
        if (parseCohortYear(clean(form, "cohortYear")) == null) {
            errors.add("Choose an entry year.");
        }

        return errors;
    }

    @Transactional 
    public User createUserAndStudentProfile (String email, String displayName, String password,
                                             Program program, int cohortYear) {
        // The User constructor trims and lowercases the email
        User user = userRepository.save(new User(email, passwordEncoder.encode(password), displayName.trim(), Role.STUDENT));

        // The profile shares the user's id, so save the user first
        studentProfileRepository.save(new StudentProfile(user, program, cohortYear));
        return user;
    }

    // Form value with surrounding spaces removed; missing fields count as empty
    private static String clean(Map<String, String> form, String key) {
        return raw(form, key).trim();
    }

    // Form value as typed; missing fields count as empty
    private static String raw(Map<String, String> form, String key) {
        String value = form.get(key);
        if (value == null) {
            return "";
        }
        return value;
    }

    // Returns the Program for a form value, or null if it is blank or not a real program.
    public static Program parseProgram(String value) {
        try {
            return Program.valueOf(value.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    // Returns the entry year for a form value, or null if it is not a number in COHORT_YEARS.
    public static Integer parseCohortYear(String value) {
        try {
            int year = Integer.parseInt(value.trim());
            if (COHORT_YEARS.contains(year)){
                return year;
            }
            return null;
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }
}
