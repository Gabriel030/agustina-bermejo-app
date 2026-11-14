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
import com.red_escuela.backend.dataimport.repository.ImportBatchEventRepository;
import com.red_escuela.backend.dataimport.repository.ImportBatchRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.dataimport.exception.ImportInstitutionNotFoundException;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ImportBatchCreator {
    private static final List<ImportBatchStatus> RUNNING = List.of(ImportBatchStatus.VALIDATING,
            ImportBatchStatus.COMMITTING, ImportBatchStatus.ROLLING_BACK);

    private final ImportBatchRepository batchRepository;
    private final ImportBatchEventRepository eventRepository;
    private final InstitutionRepository institutionRepository;
    private final BillingModeGate billingModeGate;
    private final AuditTrail auditTrail;

    public ImportBatchCreator(ImportBatchRepository batchRepository, ImportBatchEventRepository eventRepository,
            InstitutionRepository institutionRepository, BillingModeGate billingModeGate,
            AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.institutionRepository = institutionRepository;
        this.billingModeGate = billingModeGate;
    }

    @Transactional
    public Long create(String name, Long actorPersonId) {
        if (billingModeGate.currentMode() != BillingMode.OFF) {
            throw new BillingModeNotOffException();
        }
        Institution institution = institutionRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ImportInstitutionNotFoundException());
        if (batchRepository.existsByInstitutionIdAndStatusIn(institution.getId(), RUNNING)) {
            throw new ImportAlreadyRunningException();
        }
        var now = ApplicationTime.now();
        ImportBatch batch = batchRepository.save(ImportBatch.draft(name.strip(), institution.getId(), actorPersonId, now));
        eventRepository.save(new ImportBatchEvent(batch.getId(), ImportBatchEventType.CREATED, actorPersonId, now,
                Map.of("name", batch.getName())));
        auditTrail.record(new AuditRecord(AuditAction.DATA_IMPORT_CREATED, AuditEntityType.DATA_IMPORT_BATCH, batch.getId(),
                null, null, null));
        return batch.getId();
    }
}
