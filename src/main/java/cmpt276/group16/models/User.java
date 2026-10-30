package cmpt276.group16.models;

import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.*;


@Entity
@Table(name = "users")
// Serializable so login can store the user in the session (sessions are saved in the database)
public class User implements Serializable {

    // Keeps saved sessions readable
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stored lowercase so is case insensitive
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    // Store as string
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private Instant createdAt;

    protected User() {}

    public User(String email, String passwordHash, String displayName, Role role) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    // Runs just before the first save, so every user gets a creation time
    @PrePersist
    void beforeFirstSave() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Make email case insensitive
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
