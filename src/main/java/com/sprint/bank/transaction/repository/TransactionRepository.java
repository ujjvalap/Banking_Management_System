package com.sprint.bank.transaction.repository;

import com.sprint.bank.transaction.entity.Transaction;
import com.sprint.bank.transaction.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @EntityGraph(attributePaths = "account")
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.account.id = :accountId
              AND (:type IS NULL OR t.type = :type)
              AND (:start IS NULL OR t.timestamp >= :start)
              AND (:end IS NULL OR t.timestamp <= :end)
            ORDER BY t.timestamp DESC, t.id DESC
            """)
    Page<Transaction> findAccountStatement(
            @Param("accountId") Long accountId,
            @Param("type") TransactionType type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @EntityGraph(attributePaths = "account")
    @Query("""
            SELECT t FROM Transaction t
            WHERE (:type IS NULL OR t.type = :type)
              AND (:start IS NULL OR t.timestamp >= :start)
              AND (:end IS NULL OR t.timestamp <= :end)
            ORDER BY t.timestamp DESC, t.id DESC
            """)
    Page<Transaction> findFilteredTransactions(
            @Param("type") TransactionType type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @EntityGraph(attributePaths = "account")
    List<Transaction> findTop20ByAccountCustomerUserEmailOrderByTimestampDescIdDesc(String email);

    long countByAccountCustomerUserEmail(String email);

    List<Transaction> findByReferenceId(String referenceId);
}
