package com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityInput;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.policy.ApprovedSubjectsEligibilityPolicy;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsActor;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsCertificateIssuing;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsEligibilityFacts;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.ApprovedSubjectsRefusals;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnApprovedSubjectsEnrollments;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.port.OwnedEnrollment;
import com.red_escuela.backend.graduation.procedure.exception.IssuanceLimitReachedException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureNotEligibleException;
import com.red_escuela.backend.shared.module.BillingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedSubjectsCertificateRequesterTest {

    private static final Long STUDENT_ID = 10L;
    private static final Long PERSON_ID = 77L;
    private static final Long OWN_ENROLLMENT = 100L;
    private static final Long OTHER_ENROLLMENT = 200L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC);

    private final Map<Long, OwnedEnrollment> ownedByStudent = Map.of(OWN_ENROLLMENT, new OwnedEnrollment(OWN_ENROLLMENT, true));
    private ApprovedSubjectsEligibilityInput input;
    private final List<String> issued = new ArrayList<>();
    private ApprovedSubjectsCertificateRequester service;

    @BeforeEach
    void setUp() {
        input = eligibleInput();
        ApprovedSubjectsActor actor = new ApprovedSubjectsActor() {
            public Long currentStudentId() {
                return STUDENT_ID;
            }

            public Long currentPersonId() {
                return PERSON_ID;
            }
        };
        OwnApprovedSubjectsEnrollments enrollments = (studentId, enrollmentId) ->
                studentId.equals(STUDENT_ID) ? Optional.ofNullable(ownedByStudent.get(enrollmentId)) : Optional.empty();
        ApprovedSubjectsEligibilityFacts facts = (studentId, enrollment, now) -> input;
        ApprovedSubjectsCertificateIssuing issuing = (studentId, enrollmentId, issuedById, now) -> {
            issued.add(studentId + ":" + enrollmentId + ":" + issuedById + ":" + now);
            return response(enrollmentId);
        };
        ApprovedSubjectsRefusals refusals = new ApprovedSubjectsRefusals() {
            public RuntimeException enrollmentNotFound() {
                return new ProcedureDocumentNotFoundException();
            }

            public RuntimeException notEligible() {
                return new ProcedureNotEligibleException();
            }

            public RuntimeException issuanceLimitReached() {
                return new IssuanceLimitReachedException();
            }
        };
        service = new ApprovedSubjectsCertificateRequester(actor, enrollments, facts,
                new ApprovedSubjectsEligibilityPolicy(), issuing, refusals, CLOCK);
    }

    @Test
    void ownEligibleEnrollmentIssuesTheCertificate() {
        ApprovedSubjectsCertificateResponse response = service.request(new RequestApprovedSubjectsCertificateRequest(OWN_ENROLLMENT));

        assertThat(response.enrollmentId()).isEqualTo(OWN_ENROLLMENT);
        assertThat(issued).containsExactly("10:100:77:" + LocalDateTime.now(CLOCK));
    }

    @Test
    void anotherStudentsEnrollmentIsNotFoundAndNothingIsIssued() {
        assertThatThrownBy(() -> service.request(new RequestApprovedSubjectsCertificateRequest(OTHER_ENROLLMENT)))
                .isInstanceOf(ProcedureDocumentNotFoundException.class);
        assertThat(issued).isEmpty();
    }

    @Test
    void inactiveEnrollmentIsNotEligible() {
        input = new ApprovedSubjectsEligibilityInput(BillingMode.OFF, false, true, 0, 1, 3, false);

        assertThatThrownBy(() -> service.request(new RequestApprovedSubjectsCertificateRequest(OWN_ENROLLMENT)))
                .isInstanceOf(ProcedureNotEligibleException.class);
        assertThat(issued).isEmpty();
    }

    @Test
    void nonRegularStudentIsNotEligible() {
        input = new ApprovedSubjectsEligibilityInput(BillingMode.OFF, false, false, 0, 1, 3, true);

        assertThatThrownBy(() -> service.request(new RequestApprovedSubjectsCertificateRequest(OWN_ENROLLMENT)))
                .isInstanceOf(ProcedureNotEligibleException.class);
        assertThat(issued).isEmpty();
    }

    @Test
    void overdueDebtWithFullBillingIsNotEligible() {
        input = new ApprovedSubjectsEligibilityInput(BillingMode.FULL, true, true, 0, 1, 3, true);

        assertThatThrownBy(() -> service.request(new RequestApprovedSubjectsCertificateRequest(OWN_ENROLLMENT)))
                .isInstanceOf(ProcedureNotEligibleException.class);
        assertThat(issued).isEmpty();
    }

    @Test
    void noApprovedSubjectsIsNotEligible() {
        input = new ApprovedSubjectsEligibilityInput(BillingMode.OFF, false, true, 0, 1, 0, true);

        assertThatThrownBy(() -> service.request(new RequestApprovedSubjectsCertificateRequest(OWN_ENROLLMENT)))
                .isInstanceOf(ProcedureNotEligibleException.class);
        assertThat(issued).isEmpty();
    }

    @Test
    void dailyLimitReachedIsReportedOnItsOwn() {
        input = new ApprovedSubjectsEligibilityInput(BillingMode.OFF, false, true, 1, 1, 3, true);

        assertThatThrownBy(() -> service.request(new RequestApprovedSubjectsCertificateRequest(OWN_ENROLLMENT)))
                .isInstanceOf(IssuanceLimitReachedException.class);
        assertThat(issued).isEmpty();
    }

    @Test
    void dailyLimitCombinedWithAnotherBlockerIsNotEligible() {
        input = new ApprovedSubjectsEligibilityInput(BillingMode.OFF, false, false, 1, 1, 3, true);

        assertThatThrownBy(() -> service.request(new RequestApprovedSubjectsCertificateRequest(OWN_ENROLLMENT)))
                .isInstanceOf(ProcedureNotEligibleException.class);
        assertThat(issued).isEmpty();
    }

    private static ApprovedSubjectsEligibilityInput eligibleInput() {
        return new ApprovedSubjectsEligibilityInput(BillingMode.OFF, false, true, 0, 1, 3, true);
    }

    private static ApprovedSubjectsCertificateResponse response(Long enrollmentId) {
        return new ApprovedSubjectsCertificateResponse(1L, "CODE", STUDENT_ID, "Ana", "Carrera", enrollmentId, null, null, null, null, 3, 5, 60, "url");
    }
}
