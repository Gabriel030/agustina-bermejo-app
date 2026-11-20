package com.red_escuela.backend.dataimport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "import_batch_event")
@NoArgsConstructor
public class ImportBatchEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private Long batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private ImportBatchEventType type;

    @Column(name = "actor_person_id", updatable = false)
    private Long actorPersonId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> detail;

    public ImportBatchEvent(Long batchId, ImportBatchEventType type, Long actorPersonId, LocalDateTime occurredAt,
            Map<String, Object> detail) {
        this.batchId = batchId;
        this.type = type;
        this.actorPersonId = actorPersonId;
        this.occurredAt = occurredAt;
        this.detail = detail;
    }
}
