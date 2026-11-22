package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.OwnGraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OwnGraduationCertificateCatalog {

    private final GraduationCertificateRepository repository;
    private final CurrentActorFinder currentActorFinder;

    public OwnGraduationCertificateCatalog(GraduationCertificateRepository repository, CurrentActorFinder currentActorFinder) {
        this.repository = repository;
        this.currentActorFinder = currentActorFinder;
    }

    public List<OwnGraduationCertificateResponse> list() {
        return repository.findByStudentIdOrderByIssuedAtDescIdDesc(currentActorFinder.currentStudentId()).stream()
                .map(OwnGraduationCertificateCatalog::toResponse)
                .toList();
    }

    private static OwnGraduationCertificateResponse toResponse(GraduationCertificate certificate) {
        boolean available = certificate.isRevocable();
        return new OwnGraduationCertificateResponse(certificate.getId(), certificate.getRegistryNumber(), certificate.getKind(),
                certificate.getStatus(), certificate.getSnapshot().titleName(), certificate.getGraduationDate(),
                certificate.getIssuedAt(), certificate.getRevokedAt(), available, available);
    }
}
