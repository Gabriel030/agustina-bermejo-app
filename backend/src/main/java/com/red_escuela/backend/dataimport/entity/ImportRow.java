package com.red_escuela.backend.dataimport.entity;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "import_row")
@NoArgsConstructor
public class ImportRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private ImportEntityType entityType;

    @Column(name = "row_number", nullable = false, updatable = false)
    private int rowNumber;

    @Column(name = "natural_key", nullable = false, length = 255)
    private String naturalKey;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, String> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportRowStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "errors", columnDefinition = "jsonb")
    private List<ImportRowError> errors;

    @Column(name = "committed_entity_id")
    private Long committedEntityId;

    public ImportRow(Long batchId, ImportEntityType entityType, int rowNumber, String naturalKey, String contentHash,
            Map<String, String> payload) {
        this.batchId = batchId;
        this.entityType = entityType;
        this.rowNumber = rowNumber;
        this.naturalKey = naturalKey;
        this.contentHash = contentHash;
        this.payload = payload;
        this.status = ImportRowStatus.PENDING;
    }

    public void classify(ImportRowStatus newStatus, List<ImportRowError> rowErrors) {
        this.status = newStatus;
        this.errors = rowErrors.isEmpty() ? null : rowErrors;
    }

    public void markCommitted(Long entityId) {
        this.status = ImportRowStatus.COMMITTED;
        this.committedEntityId = entityId;
        this.errors = null;
    }

    public void markFailed(List<ImportRowError> rowErrors) {
        this.status = ImportRowStatus.FAILED;
        this.errors = rowErrors;
    }
}
