package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "hl7_audit_logs", indexes = {
        @Index(name = "idx_hl7_control_id", columnList = "messageControlId", unique = true),
        @Index(name = "idx_hl7_correlation_id", columnList = "correlationId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hl7AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String messageControlId; // MSH-10 Message Control ID

    @Column(nullable = false)
    private String correlationId; // UUID generated for end-to-end tracing

    private String sendingFacility; // MSH-3
    private String sendingApplication; // MSH-4
    private String eventType; // MSH-9 (e.g. ADT^A01)
    
    @Column(nullable = false)
    private String payloadHash; // SHA-256 hash of payload for duplicate detection

    @Column(nullable = false)
    private String status; // RECEIVED, VALIDATED, TRANSFORMED, DELIVERED, REJECTED_DUPLICATE, FAILED

    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;
}
