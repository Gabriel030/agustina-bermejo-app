package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RegularCertificateReader {

    private final RegularStudentCertificateRepository certificateRepository;
    private final RegularCertificateRequestRepository requestRepository;
    private final CertificateVerificationUrl verificationUrl;
    private final Clock clock;

    public RegularCertificateReader(RegularStudentCertificateRepository certificateRepository,
                                    RegularCertificateRequestRepository requestRepository,
                                    CertificateVerificationUrl verificationUrl, Clock clock) {
        this.certificateRepository = certificateRepository;
        this.requestRepository = requestRepository;
        this.verificationUrl = verificationUrl;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RegularCertificateResponse get(Long id) {
        return toResponse(certificateRepository.findById(id).orElseThrow(() -> new RegularCertificateNotFoundException()),
                LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<RegularCertificateResponse> list(Long studentId, RegularCertificateStatus status) {
        LocalDateTime now = LocalDateTime.now(clock);
        return certificatesOf(studentId, status).stream().map(certificate -> toResponse(certificate, now))
                .filter(response -> status == null || response.status() == status).toList();
    }

    @Transactional(readOnly = true)
    public List<RegularCertificateRequestResponse> listRequests(Long studentId, RegularRequestOutcome outcome) {
        return requestsOf(studentId, outcome).stream().map(this::toRequestResponse).toList();
    }

    private List<RegularStudentCertificate> certificatesOf(Long studentId, RegularCertificateStatus status) {
        if (status == null || status == RegularCertificateStatus.EXPIRED || status == RegularCertificateStatus.VALID) {
            return studentId == null
                    ? certificateRepository.findTop200ByOrderByIssuedAtDesc()
                    : certificateRepository.findTop200ByStudentIdOrderByIssuedAtDesc(studentId);
        }
        return studentId == null
                ? certificateRepository.findTop200ByStatusOrderByIssuedAtDesc(status)
                : certificateRepository.findTop200ByStudentIdAndStatusOrderByIssuedAtDesc(studentId, status);
    }

    private List<RegularCertificateRequest> requestsOf(Long studentId, RegularRequestOutcome outcome) {
        if (studentId == null && outcome == null) {
            return requestRepository.findTop200ByOrderByRequestedAtDesc();
        }
        if (studentId == null) {
            return requestRepository.findTop200ByOutcomeOrderByRequestedAtDesc(outcome);
        }
        return outcome == null
                ? requestRepository.findTop200ByStudentIdOrderByRequestedAtDesc(studentId)
                : requestRepository.findTop200ByStudentIdAndOutcomeOrderByRequestedAtDesc(studentId, outcome);
    }

    private RegularCertificateResponse toResponse(RegularStudentCertificate certificate, LocalDateTime now) {
        RegularCertificateStatus status = certificate.isExpiredAt(now) ? RegularCertificateStatus.EXPIRED : certificate.getStatus();
        return new RegularCertificateResponse(certificate.getId(), certificate.getCode(), certificate.getKind(),
                certificate.enrollmentId(), status, certificate.getIssuedAt(), certificate.getExpiresAt(),
                certificate.getStandingBasis(), certificate.getBillingModeAtIssue(),
                verificationUrl.of(certificate.certificateCode()));
    }

    private RegularCertificateRequestResponse toRequestResponse(RegularCertificateRequest request) {
        Long enrollmentId = request.getKind() == RegularityKind.PROGRAM
                ? request.getProgramEnrollmentId()
                : request.getCourseEnrollmentId();
        return new RegularCertificateRequestResponse(request.getId(), request.getStudentId(), request.getKind(), enrollmentId,
                request.getChannel(), request.getOutcome(), request.getReasons(), request.getRequestedAt(),
                request.getCertificateId());
    }
}
