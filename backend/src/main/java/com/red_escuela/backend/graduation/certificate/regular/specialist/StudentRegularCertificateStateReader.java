package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularUnavailabilityType;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityReason;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularityVerdict;
import com.red_escuela.backend.graduation.certificate.regular.dto.StudentRegularCertificateState;
import com.red_escuela.backend.graduation.certificate.regular.dto.StudentRegularCertificateStateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class StudentRegularCertificateStateReader {

    private final RegularEnrollmentFactsFinder factsFinder;
    private final RegularCertificateReader certificateReader;
    private final RegularCertificateRequestRepository requestRepository;
    private final RegularityEvaluator regularityEvaluator;
    private final RegularStandingGate standingGate;
    private final OverdueDebtProbe overdueDebtProbe;

    public StudentRegularCertificateStateReader(RegularEnrollmentFactsFinder factsFinder, RegularCertificateReader certificateReader,
                                                RegularCertificateRequestRepository requestRepository,
                                                RegularityEvaluator regularityEvaluator, RegularStandingGate standingGate,
                                                OverdueDebtProbe overdueDebtProbe) {
        this.factsFinder = factsFinder;
        this.certificateReader = certificateReader;
        this.requestRepository = requestRepository;
        this.regularityEvaluator = regularityEvaluator;
        this.standingGate = standingGate;
        this.overdueDebtProbe = overdueDebtProbe;
    }

    @Transactional(readOnly = true)
    public StudentRegularCertificateStateResponse stateOf(RegularityKind kind, Long enrollmentId, Long studentId) {
        RegularEnrollmentFacts facts = factsFinder.findOwnedBy(kind, enrollmentId, studentId);
        List<RegularCertificateResponse> history = certificateReader.historyOf(kind, enrollmentId);
        boolean staffConfirmationRequired = standingGate.requiresStaffConfirmation();
        Optional<RegularCertificateResponse> valid = history.stream()
                .filter(certificate -> certificate.status() == RegularCertificateStatus.VALID).findFirst();
        if (valid.isPresent()) {
            return response(kind, enrollmentId, StudentRegularCertificateState.VALID, staffConfirmationRequired, valid.get(), null,
                    null, null, List.of(), history);
        }
        Optional<RegularCertificateRequest> pending = pendingOf(kind, enrollmentId);
        if (pending.isPresent()) {
            return response(kind, enrollmentId, StudentRegularCertificateState.PENDING, staffConfirmationRequired, null,
                    RegularRequestResponses.of(pending.get(), facts), null, null, List.of(), history);
        }
        String lastRejection = latestOf(kind, enrollmentId).filter(RegularCertificateRequest::wasRejectedByStaff)
                .map(RegularCertificateRequest::getRejectionReason).orElse(null);
        RegularityVerdict verdict = regularityEvaluator.evaluate(kind, enrollmentId);
        if (!verdict.institutionallyRegular()) {
            return response(kind, enrollmentId, StudentRegularCertificateState.UNAVAILABLE, staffConfirmationRequired, null, null,
                    unavailabilityOf(verdict), lastRejection, verdict.reasons().stream().map(RegularityReason::name).toList(), history);
        }
        if (!staffConfirmationRequired
                && (!overdueDebtProbe.isEnrollmentFeePaid(kind, enrollmentId) || overdueDebtProbe.hasOverdueDebt(studentId))) {
            return response(kind, enrollmentId, StudentRegularCertificateState.UNAVAILABLE, false, null, null,
                    RegularUnavailabilityType.PAYMENTS, lastRejection, List.of(), history);
        }
        return response(kind, enrollmentId, StudentRegularCertificateState.AVAILABLE, staffConfirmationRequired, null, null, null,
                lastRejection, List.of(), history);
    }

    private Optional<RegularCertificateRequest> pendingOf(RegularityKind kind, Long enrollmentId) {
        return kind == RegularityKind.PROGRAM
                ? requestRepository.findFirstByProgramEnrollmentIdAndOutcomeOrderByRequestedAtDesc(enrollmentId, RegularRequestOutcome.PENDING_STAFF)
                : requestRepository.findFirstByCourseEnrollmentIdAndOutcomeOrderByRequestedAtDesc(enrollmentId, RegularRequestOutcome.PENDING_STAFF);
    }

    private Optional<RegularCertificateRequest> latestOf(RegularityKind kind, Long enrollmentId) {
        return kind == RegularityKind.PROGRAM
                ? requestRepository.findFirstByProgramEnrollmentIdOrderByRequestedAtDescIdDesc(enrollmentId)
                : requestRepository.findFirstByCourseEnrollmentIdOrderByRequestedAtDescIdDesc(enrollmentId);
    }

    private RegularUnavailabilityType unavailabilityOf(RegularityVerdict verdict) {
        return verdict.reasons().contains(RegularityReason.FREE_IN_ACTIVE_SECTION)
                ? RegularUnavailabilityType.ATTENDANCE
                : RegularUnavailabilityType.ENROLLMENT;
    }

    private StudentRegularCertificateStateResponse response(RegularityKind kind, Long enrollmentId, StudentRegularCertificateState state,
                                                            boolean staffConfirmationRequired, RegularCertificateResponse certificate,
                                                            RegularCertificateRequestResponse pendingRequest,
                                                            RegularUnavailabilityType unavailableType, String rejectionReason,
                                                            List<String> reasons, List<RegularCertificateResponse> history) {
        return new StudentRegularCertificateStateResponse(kind, enrollmentId, state, staffConfirmationRequired, certificate,
                pendingRequest, unavailableType, rejectionReason, reasons, history);
    }
}
