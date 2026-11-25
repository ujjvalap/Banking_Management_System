package com.sprint.bank.audit.repository;

import com.sprint.bank.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop50ByOrderByCreatedAtDesc();

    List<AuditLog> findByActorEmailOrderByCreatedAtDesc(String actorEmail);
}
