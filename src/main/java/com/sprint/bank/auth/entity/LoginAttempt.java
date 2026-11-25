package com.sprint.bank.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "login_attempts")
@Getter
@Setter
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    protected LoginAttempt() {
    }

    public LoginAttempt(String email) {
        this.email = email;
        this.failedAttempts = 0;
        this.lastAttemptAt = Instant.now();
    }

    public boolean isCurrentlyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void reset() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastAttemptAt = Instant.now();
    }

    public void registerFailure(int maxAttempts, long lockDurationMinutes, Instant now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            this.failedAttempts = 0;
            this.lockedUntil = null;
        }
        this.failedAttempts++;
        this.lastAttemptAt = now;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = now.plusSeconds(lockDurationMinutes * 60L);
        }
    }
}
