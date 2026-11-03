package cmpt276.group16.services;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Password rules shared by registration and the profile page, so both always enforce the same standard
public final class PasswordRules {

    // At least 8 characters; BCrypt only uses the first 72 bytes, so longer passwords are rejected
    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordRules() {}

    // Returns every rule the password breaks; an empty list means it's allowed
    public static List<String> problems(String password) {
        List<String> problems = new ArrayList<>();
        if (password.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            problems.add("Password is too long (at most 72 characters, fewer if it uses accented or non-English letters).");
        }
        return problems;
    }
}
