package com.red_escuela.backend.graduation.certificate.signatory.specialist;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryDetailResponse;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SaveCertificateSignatoryRequest;
import com.red_escuela.backend.graduation.certificate.signatory.dto.SignatoryDocumentType;
import com.red_escuela.backend.graduation.certificate.signatory.entity.CertificateSignatory;
import com.red_escuela.backend.graduation.certificate.signatory.exception.SignatoryRoleInvalidException;
import com.red_escuela.backend.graduation.certificate.signatory.repository.CertificateSignatoryRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CertificateSignatoryWriter {

    private static final int MAX_AUDIT_VALUE = 100;

    private final CertificateSignatoryRepository repository;
    private final CertificateSignatoryFinder finder;
    private final CertificateSignatoryReader reader;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public CertificateSignatoryWriter(CertificateSignatoryRepository repository, CertificateSignatoryFinder finder,
                                      CertificateSignatoryReader reader, AuditTrail auditTrail, Clock clock) {
        this.repository = repository;
        this.finder = finder;
        this.reader = reader;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public CertificateSignatoryDetailResponse create(SaveCertificateSignatoryRequest request) {
        String name = request.name().strip();
        String role = requireRole(request.role());
        Set<SignatoryDocumentType> types = typesOf(request);
        CertificateSignatory signatory = repository.saveAndFlush(CertificateSignatory.create(name, role, types, LocalDateTime.now(clock)));
        audit(AuditAction.CERTIFICATE_SIGNATORY_CREATED, signatory.getId(), null, label(name, role));
        if (!types.isEmpty()) {
            audit(AuditAction.CERTIFICATE_SIGNATORY_PERMS_CHANGED, signatory.getId(), "", describe(types));
        }
        return reader.detailOf(signatory);
    }

    @Transactional
    public CertificateSignatoryDetailResponse update(Long id, SaveCertificateSignatoryRequest request) {
        CertificateSignatory signatory = finder.find(id);
        String name = request.name().strip();
        String role = requireRole(request.role());
        Set<SignatoryDocumentType> types = typesOf(request);
        String beforeLabel = label(signatory.getName(), signatory.getRole());
        String beforeTypes = describe(signatory.getAllowedDocumentTypes());
        boolean textChanged = !signatory.getName().equals(name) || !signatory.getRole().equals(role);
        boolean typesChanged = !signatory.getAllowedDocumentTypes().equals(types);
        signatory.edit(name, role, types, LocalDateTime.now(clock));
        repository.saveAndFlush(signatory);
        if (textChanged) {
            audit(AuditAction.CERTIFICATE_SIGNATORY_UPDATED, id, beforeLabel, label(name, role));
        }
        if (typesChanged) {
            audit(AuditAction.CERTIFICATE_SIGNATORY_PERMS_CHANGED, id, beforeTypes, describe(types));
        }
        return reader.detailOf(signatory);
    }

    @Transactional
    public CertificateSignatoryDetailResponse changeActive(Long id, boolean active) {
        CertificateSignatory signatory = finder.find(id);
        if (signatory.isActive() != active) {
            signatory.changeActive(active, LocalDateTime.now(clock));
            repository.saveAndFlush(signatory);
            audit(active ? AuditAction.CERTIFICATE_SIGNATORY_ACTIVATED : AuditAction.CERTIFICATE_SIGNATORY_DEACTIVATED, id,
                    String.valueOf(!active), String.valueOf(active));
        }
        return reader.detailOf(signatory);
    }

    private void audit(AuditAction action, Long id, String before, String after) {
        auditTrail.record(new AuditRecord(action, AuditEntityType.CERTIFICATE_SIGNATORY, id, before, after, null));
    }

    private String requireRole(String role) {
        String stripped = role == null ? "" : role.strip();
        if (stripped.isEmpty() || stripped.length() > 80) {
            throw new SignatoryRoleInvalidException();
        }
        return stripped;
    }

    private Set<SignatoryDocumentType> typesOf(SaveCertificateSignatoryRequest request) {
        Set<SignatoryDocumentType> types = EnumSet.noneOf(SignatoryDocumentType.class);
        types.addAll(request.allowedDocumentTypes());
        return types;
    }

    private String label(String name, String role) {
        return truncate(name + " / " + role);
    }

    private String describe(Set<SignatoryDocumentType> types) {
        return truncate(types.stream().sorted(Comparator.naturalOrder()).map(Enum::name).collect(Collectors.joining(",")));
    }

    private String truncate(String value) {
        return value.length() <= MAX_AUDIT_VALUE ? value : value.substring(0, MAX_AUDIT_VALUE);
    }
}
