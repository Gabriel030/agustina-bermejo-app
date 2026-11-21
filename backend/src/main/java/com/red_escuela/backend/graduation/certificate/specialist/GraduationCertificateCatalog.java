package com.red_escuela.backend.graduation.certificate.specialist;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateFilter;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateSummaryResponse;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class GraduationCertificateCatalog {

    private final GraduationCertificateRepository repository;
    private final CertificateVerificationUrl verificationUrl;

    public GraduationCertificateCatalog(GraduationCertificateRepository repository, CertificateVerificationUrl verificationUrl) {
        this.repository = repository;
        this.verificationUrl = verificationUrl;
    }

    public Page<GraduationCertificateSummaryResponse> list(GraduationCertificateFilter filter, Pageable pageable) {
        return repository.search(
                        filter.kind() == null ? null : filter.kind().name(),
                        filter.status() == null ? null : filter.status().name(),
                        likePattern(filter.query()),
                        startOf(filter.issuedFrom()),
                        startOfNextDay(filter.issuedTo()),
                        PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(this::toSummary);
    }

    public GraduationCertificateDetailResponse get(Long id) {
        GraduationCertificate certificate = repository.findById(id)
                .orElseThrow(() -> new GraduationCertificateNotFoundException(id));
        CertificateSnapshot snapshot = certificate.getSnapshot();
        return new GraduationCertificateDetailResponse(certificate.getId(), certificate.getCode(),
                certificate.getRegistryNumber(), certificate.getKind(), certificate.getStatus(), snapshot.holderFullName(),
                snapshot.documentTypeLabel(), snapshot.documentNumber(), snapshot.titleName(), snapshot.hours(),
                certificate.getGraduationDate(), certificate.getIssuedAt(), verificationUrl.of(certificate.certificateCode()),
                null, certificate.getRevokedAt(), null, null, null);
    }

    public GraduationCertificateSummaryResponse summaryOf(Long id) {
        return toSummary(repository.findById(id).orElseThrow(() -> new GraduationCertificateNotFoundException(id)));
    }

    private GraduationCertificateSummaryResponse toSummary(GraduationCertificate certificate) {
        CertificateSnapshot snapshot = certificate.getSnapshot();
        return new GraduationCertificateSummaryResponse(certificate.getId(), certificate.getCode(),
                certificate.getRegistryNumber(), certificate.getKind(), certificate.getStatus(), snapshot.holderFullName(),
                snapshot.documentNumber(), snapshot.titleName(), certificate.getGraduationDate(), certificate.getIssuedAt(), null);
    }

    private String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private LocalDateTime startOf(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime startOfNextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }
}
