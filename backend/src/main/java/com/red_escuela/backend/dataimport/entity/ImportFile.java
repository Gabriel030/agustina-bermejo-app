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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "import_file")
@NoArgsConstructor
public class ImportFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private ImportEntityType entityType;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public ImportFile(Long batchId, ImportEntityType entityType, String originalName, String sha256, long sizeBytes,
            int rowCount, LocalDateTime uploadedAt) {
        this.batchId = batchId;
        this.entityType = entityType;
        this.originalName = originalName;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
        this.rowCount = rowCount;
        this.uploadedAt = uploadedAt;
    }
}
