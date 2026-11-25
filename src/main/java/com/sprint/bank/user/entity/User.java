package com.sprint.bank.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sprint.bank.auth.entity.RefreshToken;
import com.sprint.bank.auth.enums.AuthProvider;
import com.sprint.bank.customer.entity.CustomerProfile;
import com.sprint.bank.user.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @JsonIgnore
    @Column(nullable = false, length = 255)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 32)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerProfile customerProfile;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    protected User() {
    }

    public User(String email, String password, Role role, boolean enabled) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
        this.authProvider = AuthProvider.LOCAL;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markEmailVerified() {
        this.enabled = true;
    }

    public void markEmailUnverified() {
        this.enabled = false;
    }

    public void revokeSessions() {
        this.tokenVersion++;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
