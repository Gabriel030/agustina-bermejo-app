package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CertificateDocumentAuditRecorder {

    private final AuditTrail auditTrail;

    public CertificateDocumentAuditRecorder(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void downloaded(String documentType, Long documentId, String sha256, String engineVersion) {
        auditTrail.record(new AuditRecord(AuditAction.DOCUMENT_PDF_DOWNLOADED, AuditEntityType.CERTIFICATE_DOCUMENT, documentId,
                null, sha256, describe(documentType, engineVersion)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void generatedByStudent(String documentType, Long documentId, String sha256, String engineVersion) {
        auditTrail.record(new AuditRecord(AuditAction.DOCUMENT_GENERATED_BY_STUDENT, AuditEntityType.CERTIFICATE_DOCUMENT, documentId,
                null, sha256, describe(documentType, engineVersion)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void hashMismatch(String documentType, Long documentId, String expectedSha256, String regeneratedSha256, String engineVersion) {
        auditTrail.record(new AuditRecord(AuditAction.DOCUMENT_PDF_HASH_MISMATCH, AuditEntityType.CERTIFICATE_DOCUMENT, documentId,
                expectedSha256, regeneratedSha256, describe(documentType, engineVersion)));
    }

    private static String describe(String documentType, String engineVersion) {
        return "documentType=" + documentType + ";engineVersion=" + engineVersion;
    }
}
