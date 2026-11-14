package com.red_escuela.backend.dataimport.specialist;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.dataimport.entity.ImportBatch;
import com.red_escuela.backend.dataimport.entity.ImportBatchEvent;
import com.red_escuela.backend.dataimport.entity.ImportBatchEventType;
import com.red_escuela.backend.dataimport.entity.ImportBatchStatus;
import com.red_escuela.backend.dataimport.exception.BillingModeNotOffException;
import com.red_escuela.backend.dataimport.exception.ImportAlreadyRunningException;
import com.red_escuela.backend.dataimport.exception.ImportBatchNotFoundException;
import com.red_escuela.backend.dataimport.exception.ImportBatchStateInvalidException;
import com.red_escuela.backend.dataimport.exception.ImportRollbackBlockedException;
import com.red_escuela.backend.dataimport.repository.ImportBatchEventRepository;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportBatchLifecycleRecorder {
    private final ImportBatchRepository batchRepository;
    private final ImportBatchEventRepository eventRepository;
    private final BillingModeGate billingModeGate;
    private final AuditTrail auditTrail;

    public ImportBatchLifecycleRecorder(ImportBatchRepository batchRepository,
            ImportBatchEventRepository eventRepository, BillingModeGate billingModeGate,
            AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.billingModeGate = billingModeGate;
    }

    @Transactional
    public void seal(Long batchId, Long actorPersonId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        if (!batch.canSeal()) {
            throw new ImportBatchStateInvalidException("cerrar", batch.getStatus().name());
        }
        LocalDateTime now = ApplicationTime.now();
        batch.seal(actorPersonId, now);
        eventRepository.save(new ImportBatchEvent(batchId, ImportBatchEventType.SEALED, actorPersonId, now, Map.of()));
        auditTrail.record(new AuditRecord(AuditAction.DATA_IMPORT_SEALED, AuditEntityType.DATA_IMPORT_BATCH, batchId,
                null, null, null));
    }

    @Transactional
    public ImportBatchStatus startRollback(Long batchId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        if (billingModeGate.currentMode() != BillingMode.OFF) {
            throw new BillingModeNotOffException();
        }
        if (!batch.canRollback()) {
            throw new ImportBatchStateInvalidException("deshacer", batch.getStatus().name());
        }
        ImportBatchStatus previous = batch.getStatus();
        batch.startRollback();
        try {
            batchRepository.saveAndFlush(batch);
        } catch (DataIntegrityViolationException e) {
            throw new ImportAlreadyRunningException();
        }
        return previous;
    }

    @Transactional
    public void abortRollback(Long batchId, ImportBatchStatus previous, Long actorPersonId, RuntimeException failure) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new ImportBatchNotFoundException(batchId));
        batch.restoreStatus(previous);
        Map<String, Object> detail = new LinkedHashMap<>();
        if (failure instanceof ImportRollbackBlockedException blocked) {
            detail.put("entityType", blocked.entityType().name());
            detail.put("naturalKey", blocked.naturalKey());
            detail.put("reason", blocked.reason());
        } else {
            detail.put("failure", failure.getClass().getSimpleName());
        }
        eventRepository.save(new ImportBatchEvent(batchId, ImportBatchEventType.ROLLBACK_BLOCKED, actorPersonId,
                ApplicationTime.now(), detail));
        auditTrail.record(new AuditRecord(AuditAction.DATA_IMPORT_ROLLBACK_BLOCKED, AuditEntityType.DATA_IMPORT_BATCH, batchId,
                null, null, null));
    }
}
