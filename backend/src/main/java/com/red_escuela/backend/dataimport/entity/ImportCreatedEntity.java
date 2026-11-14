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
@Table(name = "import_created_entity")
@NoArgsConstructor
public class ImportCreatedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private ImportEntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Column(name = "natural_key", nullable = false, updatable = false, length = 255)
    private String naturalKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ImportCreatedEntity(Long batchId, ImportEntityType entityType, Long entityId, String naturalKey,
            LocalDateTime createdAt) {
        this.batchId = batchId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.naturalKey = naturalKey;
        this.createdAt = createdAt;
    }
}
