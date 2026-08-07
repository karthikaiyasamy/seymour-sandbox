package com.healthcare.sandbox.repository;

import com.healthcare.sandbox.model.Hl7AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Hl7AuditLogRepository extends JpaRepository<Hl7AuditLog, Long> {
    Optional<Hl7AuditLog> findByMessageControlId(String messageControlId);
    Optional<Hl7AuditLog> findByPayloadHash(String payloadHash);
}
