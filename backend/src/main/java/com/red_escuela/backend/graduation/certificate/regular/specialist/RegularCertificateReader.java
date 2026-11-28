package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificatePage;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularStudentCertificate;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateSpecifications;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularStudentCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.enrollment.api.EnrollmentNameFinder;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RegularCertificateReader {

    private static final int MAX_PAGE_SIZE = 200;
    private static final List<CertificateExceptionStatus> OPEN_STATUSES = List.of(CertificateExceptionStatus.PENDING, CertificateExceptionStatus.APPROVED);

    private final RegularStudentCertificateRepository certificateRepository;
    private final CertificateExceptionApprovalRepository exceptionRepository;
    private final RegularDebtFactsReader debtFactsReader;
    private final RegularCertificateRequestRepository requestRepository;
    private final CertificateVerificationUrl verificationUrl;
    private final PersonNameFinder personNameFinder;
    private final EnrollmentNameFinder enrollmentNameFinder;
    private final Clock clock;

    public RegularCertificateReader(RegularStudentCertificateRepository certificateRepository,
                                    CertificateExceptionApprovalRepository exceptionRepository, RegularDebtFactsReader debtFactsReader,
                                    RegularCertificateRequestRepository requestRepository,
                                    CertificateVerificationUrl verificationUrl, PersonNameFinder personNameFinder,
                                    EnrollmentNameFinder enrollmentNameFinder, Clock clock) {
        this.certificateRepository = certificateRepository;
        this.exceptionRepository = exceptionRepository;
        this.debtFactsReader = debtFactsReader;
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
        return listPage(studentId, status, null, null).items();
    }

    @Transactional(readOnly = true)
    public CertificatePage<RegularCertificateResponse> listPage(Long studentId, RegularCertificateStatus status, Integer page, Integer size) {
        LocalDateTime now = LocalDateTime.now(clock);
        Page<RegularStudentCertificate> certificates = certificateRepository.findAll(
                RegularCertificateSpecifications.certificatesOf(studentId, status, now),
                pageOf(page, size, Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("id"))));
        return new CertificatePage<>(responsesOf(certificates.getContent(), now), certificates.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<RegularCertificateRequestResponse> listRequests(Long studentId, RegularRequestOutcome outcome) {
        return listRequestsPage(studentId, outcome, null, null).items();
    }

    @Transactional(readOnly = true)
    public CertificatePage<RegularCertificateRequestResponse> listRequestsPage(Long studentId, RegularRequestOutcome outcome,
                                                                              Integer page, Integer size) {
        Page<RegularCertificateRequest> found = requestRepository.findAll(RegularCertificateSpecifications.requestsOf(studentId, outcome),
                pageOf(page, size, Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id"))));
        List<RegularCertificateRequest> requests = found.getContent();
        Map<Long, String> studentNames = personNameFinder.findFullNamesByStudentId(
                requests.stream().map(RegularCertificateRequest::getStudentId).distinct().toList());
        Map<Long, String> programNames = enrollmentNameFinder.findProgramNamesByProgramEnrollmentId(
                enrollmentIdsOf(requests, RegularityKind.PROGRAM));
        Map<Long, String> courseNames = enrollmentNameFinder.findCourseNamesByCourseEnrollmentId(
                enrollmentIdsOf(requests, RegularityKind.COURSE));
        Map<Long, RegularDebtFacts> debtByStudent = debtFactsReader.factsOf(
                requests.stream().map(RegularCertificateRequest::getStudentId).distinct().toList());
        boolean tracksDebt = debtFactsReader.tracksDebt();
        Map<String, CertificateExceptionApproval> openExceptions = openExceptionsOf(requests);
        return new CertificatePage<>(requests.stream().map(request -> RegularRequestResponses.of(request, studentNames.get(request.getStudentId()),
                (request.getKind() == RegularityKind.PROGRAM ? programNames : courseNames).get(request.enrollmentId()),
                tracksDebt ? debtByStudent.get(request.getStudentId()) : null,
                openExceptions.get(request.getKind() + ":" + request.enrollmentId()))).toList(), found.getTotalElements());
    }

    private PageRequest pageOf(Integer page, Integer size, Sort sort) {
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null ? MAX_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }

    @Transactional(readOnly = true)
    public List<RegularCertificateResponse> historyOf(RegularityKind kind, Long enrollmentId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<RegularStudentCertificate> certificates = kind == RegularityKind.PROGRAM
                ? certificateRepository.findTop50ByProgramEnrollmentIdOrderByIssuedAtDesc(enrollmentId)
                : certificateRepository.findTop50ByCourseEnrollmentIdOrderByIssuedAtDesc(enrollmentId);
        return responsesOf(certificates, now);
    }

    private Map<String, CertificateExceptionApproval> openExceptionsOf(List<RegularCertificateRequest> requests) {
        Map<String, CertificateExceptionApproval> openExceptions = new HashMap<>();
        List<Long> programIds = enrollmentIdsOf(requests, RegularityKind.PROGRAM);
        List<Long> courseIds = enrollmentIdsOf(requests, RegularityKind.COURSE);
        if (!programIds.isEmpty()) {
            exceptionRepository.findByProgramEnrollmentIdInAndStatusIn(programIds, OPEN_STATUSES)
                    .forEach(exception -> openExceptions.put(RegularityKind.PROGRAM + ":" + exception.enrollmentId(), exception));
        }
        if (!courseIds.isEmpty()) {
            exceptionRepository.findByCourseEnrollmentIdInAndStatusIn(courseIds, OPEN_STATUSES)
                    .forEach(exception -> openExceptions.put(RegularityKind.COURSE + ":" + exception.enrollmentId(), exception));
        }
        return openExceptions;
    }

    private List<Long> enrollmentIdsOf(List<RegularCertificateRequest> requests, RegularityKind kind) {
        return requests.stream().filter(request -> request.getKind() == kind).map(RegularCertificateRequest::enrollmentId)
                .distinct().toList();
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
