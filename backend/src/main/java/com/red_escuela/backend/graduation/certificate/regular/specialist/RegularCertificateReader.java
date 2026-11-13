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
import com.red_escuela.backend.enrollment.api.EnrollmentNameFinder;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class RegularCertificateReader {

    private final RegularStudentCertificateRepository certificateRepository;
    private final RegularCertificateRequestRepository requestRepository;
    private final CertificateVerificationUrl verificationUrl;
    private final PersonNameFinder personNameFinder;
    private final EnrollmentNameFinder enrollmentNameFinder;
    private final Clock clock;

    public RegularCertificateReader(RegularStudentCertificateRepository certificateRepository,
                                    RegularCertificateRequestRepository requestRepository,
                                    CertificateVerificationUrl verificationUrl, PersonNameFinder personNameFinder,
                                    EnrollmentNameFinder enrollmentNameFinder, Clock clock) {
        this.certificateRepository = certificateRepository;
        this.requestRepository = requestRepository;
        this.verificationUrl = verificationUrl;
        this.personNameFinder = personNameFinder;
        this.enrollmentNameFinder = enrollmentNameFinder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RegularCertificateResponse get(Long id) {
        return responsesOf(List.of(certificateRepository.findById(id).orElseThrow(() -> new RegularCertificateNotFoundException())),
                LocalDateTime.now(clock)).get(0);
    }

    @Transactional(readOnly = true)
    public List<RegularCertificateResponse> list(Long studentId, RegularCertificateStatus status) {
        LocalDateTime now = LocalDateTime.now(clock);
        return responsesOf(certificatesOf(studentId, status), now).stream()
                .filter(response -> status == null || response.status() == status).toList();
    }

    @Transactional(readOnly = true)
    public List<RegularCertificateRequestResponse> listRequests(Long studentId, RegularRequestOutcome outcome) {
        List<RegularCertificateRequest> requests = requestsOf(studentId, outcome);
        Map<Long, String> studentNames = personNameFinder.findFullNamesByStudentId(
                requests.stream().map(RegularCertificateRequest::getStudentId).distinct().toList());
        Map<Long, String> programNames = enrollmentNameFinder.findProgramNamesByProgramEnrollmentId(
                enrollmentIdsOf(requests, RegularityKind.PROGRAM));
        Map<Long, String> courseNames = enrollmentNameFinder.findCourseNamesByCourseEnrollmentId(
                enrollmentIdsOf(requests, RegularityKind.COURSE));
        return requests.stream().map(request -> RegularRequestResponses.of(request, studentNames.get(request.getStudentId()),
                (request.getKind() == RegularityKind.PROGRAM ? programNames : courseNames).get(request.enrollmentId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<RegularCertificateResponse> historyOf(RegularityKind kind, Long enrollmentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<RegularStudentCertificate> certificates = kind == RegularityKind.PROGRAM
                ? certificateRepository.findTop50ByProgramEnrollmentIdOrderByIssuedAtDesc(enrollmentId)
                : certificateRepository.findTop50ByCourseEnrollmentIdOrderByIssuedAtDesc(enrollmentId);
        return responsesOf(certificates, now);
    }

    private List<Long> enrollmentIdsOf(List<RegularCertificateRequest> requests, RegularityKind kind) {
        return requests.stream().filter(request -> request.getKind() == kind).map(RegularCertificateRequest::enrollmentId)
                .distinct().toList();
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

    private List<RegularCertificateResponse> responsesOf(List<RegularStudentCertificate> certificates, LocalDateTime now) {
        Map<Long, String> studentNames = personNameFinder.findFullNamesByStudentId(
                certificates.stream().map(RegularStudentCertificate::getStudentId).distinct().toList());
        return certificates.stream().map(certificate -> toResponse(certificate, studentNames.get(certificate.getStudentId()), now)).toList();
    }

    private RegularCertificateResponse toResponse(RegularStudentCertificate certificate, String studentName, LocalDateTime now) {
        RegularCertificateStatus status = certificate.isExpiredAt(now) ? RegularCertificateStatus.EXPIRED : certificate.getStatus();
        return new RegularCertificateResponse(certificate.getId(), certificate.getCode(), certificate.getStudentId(),
                studentName, certificate.getSnapshot().titleName(), certificate.getKind(),
                certificate.enrollmentId(), status, certificate.getIssuedAt(), certificate.getExpiresAt(),
                certificate.getStandingBasis(), certificate.getBillingModeAtIssue(),
                verificationUrl.of(certificate.certificateCode()));
    }
}
