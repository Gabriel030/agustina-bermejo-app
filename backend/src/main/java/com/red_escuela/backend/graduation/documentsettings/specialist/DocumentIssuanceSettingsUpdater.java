package com.red_escuela.backend.graduation.documentsettings.specialist;

import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.dto.UpdateDocumentIssuanceSettingsRequest;
import com.red_escuela.backend.graduation.documentsettings.entity.DocumentIssuanceSettingsEntity;
import com.red_escuela.backend.graduation.documentsettings.repository.DocumentIssuanceSettingsRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class DocumentIssuanceSettingsUpdater {

    private final DocumentIssuanceSettingsRepository repository;
    private final CurrentActorFinder currentActorFinder;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public DocumentIssuanceSettingsUpdater(DocumentIssuanceSettingsRepository repository, CurrentActorFinder currentActorFinder,
                                           AuditTrail auditTrail, Clock clock) {
        this.repository = repository;
        this.currentActorFinder = currentActorFinder;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public DocumentIssuanceSettingsResponse update(UpdateDocumentIssuanceSettingsRequest request) {
        DocumentIssuanceSettingsEntity entity = repository.findById(DocumentIssuanceSettingsEntity.SINGLETON_ID).orElseThrow();
        if (entity.getVersion() != request.version().longValue()) {
            throw new ObjectOptimisticLockingFailureException(DocumentIssuanceSettingsEntity.class, DocumentIssuanceSettingsEntity.SINGLETON_ID);
        }
        String before = describe(entity.getExamRegistrationAdvanceDays(), entity.getExamAttendanceValidityDays(),
                entity.getApprovedSubjectsValidityDays(), entity.getDailyIssuanceLimitPerType());
        entity.apply(request.examRegistrationAdvanceDays(), request.examAttendanceValidityDays(), request.approvedSubjectsValidityDays(),
                request.dailyIssuanceLimitPerType(), currentActorFinder.currentPersonId(), LocalDateTime.now(clock));
        DocumentIssuanceSettingsEntity saved = repository.saveAndFlush(entity);
        String after = describe(saved.getExamRegistrationAdvanceDays(), saved.getExamAttendanceValidityDays(),
                saved.getApprovedSubjectsValidityDays(), saved.getDailyIssuanceLimitPerType());
        auditTrail.record(new AuditRecord(AuditAction.DOCUMENT_SETTINGS_CHANGED, AuditEntityType.INSTITUTE_SETTING,
                (long) DocumentIssuanceSettingsEntity.SINGLETON_ID, before, after, null));
        return responseOf(saved);
    }

    static DocumentIssuanceSettingsResponse responseOf(DocumentIssuanceSettingsEntity entity) {
        return new DocumentIssuanceSettingsResponse(entity.getExamRegistrationAdvanceDays(), entity.getExamAttendanceValidityDays(),
                entity.getApprovedSubjectsValidityDays(), entity.getDailyIssuanceLimitPerType(), entity.getVersion());
    }

    private static String describe(int advanceDays, int attendanceValidityDays, int approvedValidityDays, int dailyLimit) {
        return "advance=" + advanceDays + ", attendance=" + attendanceValidityDays
                + ", approved=" + approvedValidityDays + ", limit=" + dailyLimit;
    }
}
