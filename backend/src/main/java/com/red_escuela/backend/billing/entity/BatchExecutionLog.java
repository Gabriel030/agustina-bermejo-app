package com.red_escuela.backend.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Versión mínima necesaria para que InvoiceOverdueSweepService/EnrollmentBillingComplianceSweepService
// (Fase 6.3) dejen rastro consultable de cada corrida — la spec completa de observabilidad
// (reintentos, alertas) es de Fase 6.11, esta entidad se adelanta como prerrequisito técnico.
@Getter
@NoArgsConstructor
@Entity
@Table(name = "batch_execution_log")
public class BatchExecutionLog {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    @Column(name = "ran_at", nullable = false)
    private LocalDateTime ranAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "candidate_count", nullable = false)
    private int candidateCount;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchExecutionStatus status;

    @Column(name = "errors")
    private String errors;

    public BatchExecutionLog(String jobName, LocalDateTime ranAt, int candidateCount, int processedCount, int failedCount,
                              long durationMs, BatchExecutionStatus status, String errors) {
        this.jobName = jobName;
        this.ranAt = ranAt;
        this.candidateCount = candidateCount;
        this.processedCount = processedCount;
        this.failedCount = failedCount;
        this.durationMs = durationMs;
        this.status = status;
        this.errors = errors;
    }
}
