package com.red_escuela.backend.dataimport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "import_batch")
@NoArgsConstructor
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ImportBatchStatus status;

    @Column(name = "institution_id", nullable = false, updatable = false)
    private Long institutionId;

    @Column(name = "created_by_person_id", nullable = false, updatable = false)
    private Long createdByPersonId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "sealed_at")
    private LocalDateTime sealedAt;

    @Column(name = "sealed_by_person_id")
    private Long sealedByPersonId;

    @Column(name = "billing_mode_at_commit", length = 20)
    private String billingModeAtCommit;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private ImportBatch(String name, Long institutionId, Long createdByPersonId, LocalDateTime createdAt) {
        this.name = name;
        this.status = ImportBatchStatus.DRAFT;
        this.institutionId = institutionId;
        this.createdByPersonId = createdByPersonId;
        this.createdAt = createdAt;
    }

    public static ImportBatch draft(String name, Long institutionId, Long createdByPersonId, LocalDateTime createdAt) {
        return new ImportBatch(name, institutionId, createdByPersonId, createdAt);
    }

    public boolean acceptsFiles() {
        return status == ImportBatchStatus.DRAFT
                || status == ImportBatchStatus.FILES_UPLOADED
                || status == ImportBatchStatus.VALIDATED
                || status == ImportBatchStatus.VALIDATION_FAILED;
    }

    public boolean canValidate() {
        return status == ImportBatchStatus.FILES_UPLOADED
                || status == ImportBatchStatus.VALIDATED
                || status == ImportBatchStatus.VALIDATION_FAILED;
    }

    public boolean canCommit() {
        return status == ImportBatchStatus.VALIDATED;
    }

    public boolean canSeal() {
        return status == ImportBatchStatus.COMMITTED;
    }

    public boolean canRollback() {
        return status == ImportBatchStatus.COMMITTED
                || status == ImportBatchStatus.PARTIAL
                || status == ImportBatchStatus.FAILED;
    }

    public boolean canRetry() {
        return status == ImportBatchStatus.PARTIAL;
    }

    public void seal(Long actorPersonId, LocalDateTime at) {
        status = ImportBatchStatus.SEALED;
        sealedAt = at;
        sealedByPersonId = actorPersonId;
    }

    public void startRollback() {
        status = ImportBatchStatus.ROLLING_BACK;
    }

    public void finishRollback() {
        status = ImportBatchStatus.ROLLED_BACK;
    }

    public void restoreStatus(ImportBatchStatus previous) {
        status = previous;
    }

    public void markFilesUploaded() {
        status = ImportBatchStatus.FILES_UPLOADED;
        validatedAt = null;
    }

    public void markValidated(boolean hasErrors, LocalDateTime at) {
        status = hasErrors ? ImportBatchStatus.VALIDATION_FAILED : ImportBatchStatus.VALIDATED;
        validatedAt = at;
    }

    public void startCommit(String billingMode) {
        status = ImportBatchStatus.COMMITTING;
        billingModeAtCommit = billingMode;
    }

    public void finishCommit(boolean allCommitted, LocalDateTime at) {
        status = allCommitted ? ImportBatchStatus.COMMITTED : ImportBatchStatus.PARTIAL;
        committedAt = at;
    }
}
