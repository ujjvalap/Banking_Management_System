package com.sprint.bank.auth.repository;

import com.sprint.bank.auth.entity.LoginAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    Optional<LoginAttempt> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginAttempt> findWithLockByEmail(String email);
}
