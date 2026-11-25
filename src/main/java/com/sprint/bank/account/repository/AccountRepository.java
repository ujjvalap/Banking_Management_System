package com.sprint.bank.account.repository;

import com.sprint.bank.account.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = {"customer", "customer.user"})
    List<Account> findByCustomerUserEmailOrderByCreatedAtDesc(String email);

    @EntityGraph(attributePaths = {"customer", "customer.user"})
    Optional<Account> findWithCustomerById(Long id);

    @EntityGraph(attributePaths = {"customer", "customer.user"})
    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    long countByCustomerId(Long customerId);

    @Query("SELECT a.customer.user.id FROM Account a WHERE a.id = ?1")
    Optional<Long> findOwnerUserIdById(Long id);

    @Query("SELECT a.id FROM Account a WHERE a.accountNumber = ?1")
    Optional<Long> findIdByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a JOIN FETCH a.customer c JOIN FETCH c.user WHERE a.id = :id")
    Optional<Account> findWithLockById(Long id);

    @EntityGraph(attributePaths = {"customer", "customer.user"})
    List<Account> findAllByOrderByIdDesc();

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a")
    BigDecimal sumAllBalances();
}
