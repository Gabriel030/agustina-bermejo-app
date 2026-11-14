package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.dataimport.entity.ImportBatch;
import com.red_escuela.backend.dataimport.entity.ImportBatchEvent;
import com.red_escuela.backend.dataimport.entity.ImportBatchEventType;
import com.red_escuela.backend.dataimport.entity.ImportBatchStatus;
import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRow;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.entity.ImportRowStatus;
import com.red_escuela.backend.dataimport.exception.BillingModeNotOffException;
import com.red_escuela.backend.dataimport.exception.ImportAlreadyRunningException;
import com.red_escuela.backend.dataimport.exception.ImportBatchNotFoundException;
import com.red_escuela.backend.dataimport.exception.ImportBatchStateInvalidException;
import com.red_escuela.backend.dataimport.exception.ImportNotValidatedException;
import com.red_escuela.backend.dataimport.repository.ImportBatchEventRepository;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.dataimport.repository.ImportRowRepository;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportBatchProgressRecorder {
    private static final List<ImportRowStatus> PENDING_COMMIT = List.of(ImportRowStatus.VALID, ImportRowStatus.FAILED);

    private final ImportBatchRepository batchRepository;
    private final ImportRowRepository rowRepository;
    private final ImportBatchEventRepository eventRepository;
    private final BillingModeGate billingModeGate;
    private final AuditTrail auditTrail;

    public ImportBatchProgressRecorder(ImportBatchRepository batchRepository, ImportRowRepository rowRepository,
            ImportBatchEventRepository eventRepository, BillingModeGate billingModeGate,
            AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.eventRepository = eventRepository;
        this.billingModeGate = billingModeGate;
    }

    @Transactional
    public Long startCommit(Long batchId, boolean onlyValidRows) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        BillingMode mode = billingModeGate.currentMode();
        if (mode != BillingMode.OFF) {
            throw new BillingModeNotOffException();
        }
        if (batch.getStatus() == ImportBatchStatus.VALIDATION_FAILED && !onlyValidRows) {
            throw new ImportNotValidatedException();
        }
        if (!batch.canCommit() && batch.getStatus() != ImportBatchStatus.VALIDATION_FAILED) {
            throw new ImportBatchStateInvalidException("importar", batch.getStatus().name());
        }
        batch.startCommit(mode.name());
        try {
            batchRepository.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            throw new ImportAlreadyRunningException();
        }
        return batch.getInstitutionId();
    }

    @Transactional(readOnly = true)
    public List<Long> nextValidRowIds(Long batchId, ImportEntityType type, int size) {
        return rowRepository.findByBatchIdAndEntityTypeAndStatusOrderByRowNumber(batchId, type, ImportRowStatus.VALID,
                PageRequest.of(0, size)).stream().map(ImportRow::getId).toList();
    }

    @Transactional
    public void markChunkFailed(List<Long> rowIds, String failure) {
        rowRepository.findAllById(rowIds).forEach(row -> row.markFailed(
                List.of(new ImportRowError(null, "CHUNK_FAILED", failure))));
    }

    @Transactional
    public Long startRetry(Long batchId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        BillingMode mode = billingModeGate.currentMode();
        if (mode != BillingMode.OFF) {
            throw new BillingModeNotOffException();
        }
        if (!batch.canRetry()) {
            throw new ImportBatchStateInvalidException("reintentar", batch.getStatus().name());
        }
        rowRepository.moveStatus(batchId, ImportRowStatus.FAILED, ImportRowStatus.VALID);
        batch.startCommit(mode.name());
        try {
            batchRepository.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            throw new ImportAlreadyRunningException();
        }
        return batch.getInstitutionId();
    }

    @Transactional
    public ImportBatch finishCommit(Long batchId, Long actorPersonId) {
        return finish(batchId, actorPersonId, ImportBatchEventType.COMMITTED);
    }

    @Transactional
    public ImportBatch finishRetry(Long batchId, Long actorPersonId) {
        return finish(batchId, actorPersonId, ImportBatchEventType.RETRIED);
    }

    private ImportBatch finish(Long batchId, Long actorPersonId, ImportBatchEventType eventType) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        long pending = rowRepository.countByBatchIdAndStatusIn(batchId, PENDING_COMMIT);
        LocalDateTime now = ApplicationTime.now();
        batch.finishCommit(pending == 0, now);
        eventRepository.save(new ImportBatchEvent(batchId, eventType, actorPersonId, now,
                Map.of("pendingRows", pending)));
        auditTrail.record(new AuditRecord(eventType == ImportBatchEventType.RETRIED ? AuditAction.DATA_IMPORT_RETRIED
                : AuditAction.DATA_IMPORT_COMMITTED, AuditEntityType.DATA_IMPORT_BATCH, batchId,
                null, batch.getStatus().name(), null));
        return batch;
    }
}
