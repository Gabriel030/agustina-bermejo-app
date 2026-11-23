package com.red_escuela.backend.graduation.certificate.regular.entity;

import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionDecisionReasonInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionReasonInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionAlreadyConsumedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionNotApprovedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.IllegalCertificateExceptionTransitionException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionTargetMismatchException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionWithdrawNotAllowedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.OrganismKindRequiredException;
import com.red_escuela.backend.graduation.certificate.regular.exception.OrganismReferenceInvalidException;
import com.red_escuela.backend.graduation.certificate.regular.exception.OwnExceptionDecisionException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateExceptionApprovalTest {

    private static final Long REQUESTER = 10L;
    private static final Long DIRECTOR = 20L;
    private static final String REASON = "El alumno presentó un reclamo formal ante Defensa del Consumidor";
    private static final String DECISION = "Reclamo verificado con el expediente";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final ValidityDays SEVEN = new ValidityDays(7);

    private CertificateExceptionApproval pending() {
        return CertificateExceptionApproval.request(RegularityKind.PROGRAM, 5L, 3L, OrganismKind.CONSUMER_DEFENSE, "EXP-123/2026", REASON,
                REQUESTER, NOW);
    }

    private CertificateExceptionApproval approved() {
        CertificateExceptionApproval approval = pending();
        approval.approve(DIRECTOR, DECISION, SEVEN, NOW.plusHours(1));
        return approval;
    }

    @Test
    void requestStartsPendingAndRecordsTheFirstHistoryEntry() {
        CertificateExceptionApproval approval = pending();

        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.PENDING);
        assertThat(approval.getProgramEnrollmentId()).isEqualTo(5L);
        assertThat(approval.getCourseEnrollmentId()).isNull();
        assertThat(approval.enrollmentId()).isEqualTo(5L);
        assertThat(approval.getStatusChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFromStatus()).isNull();
            assertThat(change.getToStatus()).isEqualTo(CertificateExceptionStatus.PENDING);
            assertThat(change.getEvent()).isEqualTo(CertificateExceptionEvent.REQUEST);
            assertThat(change.getActorPersonId()).isEqualTo(REQUESTER);
        });
    }

    @Test
    void courseRequestKeepsTheCourseEnrollment() {
        CertificateExceptionApproval approval = CertificateExceptionApproval.request(RegularityKind.COURSE, 8L, 3L, OrganismKind.COURT, "Causa 9",
                REASON, REQUESTER, NOW);

        assertThat(approval.getCourseEnrollmentId()).isEqualTo(8L);
        assertThat(approval.getProgramEnrollmentId()).isNull();
    }

    @Test
    void reasonShorterThanTwentyCharactersIsRejected() {
        assertThatThrownBy(() -> CertificateExceptionApproval.request(RegularityKind.PROGRAM, 5L, 3L, OrganismKind.COURT, "X-1",
                "a".repeat(19), REQUESTER, NOW)).isInstanceOf(CertificateExceptionReasonInvalidException.class);
        assertThatThrownBy(() -> CertificateExceptionApproval.request(RegularityKind.PROGRAM, 5L, 3L, OrganismKind.COURT, "X-1",
                " ".repeat(10) + "a".repeat(10), REQUESTER, NOW)).isInstanceOf(CertificateExceptionReasonInvalidException.class);
        assertThatThrownBy(() -> CertificateExceptionApproval.request(RegularityKind.PROGRAM, 5L, 3L, OrganismKind.COURT, "X-1",
                null, REQUESTER, NOW)).isInstanceOf(CertificateExceptionReasonInvalidException.class);
        assertThat(CertificateExceptionApproval.request(RegularityKind.PROGRAM, 5L, 3L, OrganismKind.COURT, "X-1", "a".repeat(20), REQUESTER, NOW)
                .getReason()).hasSize(20);
    }

    @Test
    void organismKindAndReferenceAreMandatory() {
        assertThatThrownBy(() -> CertificateExceptionApproval.request(RegularityKind.PROGRAM, 5L, 3L, null, "X-1", REASON, REQUESTER, NOW))
                .isInstanceOf(OrganismKindRequiredException.class);
        for (String reference : new String[]{null, "", "   ", "x".repeat(201)}) {
            assertThatThrownBy(() -> CertificateExceptionApproval.request(RegularityKind.PROGRAM, 5L, 3L, OrganismKind.OTHER, reference, REASON,
                    REQUESTER, NOW)).isInstanceOf(OrganismReferenceInvalidException.class);
        }
    }

    @Test
    void approveSetsValidUntilFromConfiguredValidityAndRecordsWhoAndWhen() {
        CertificateExceptionApproval approval = pending();
        LocalDateTime decidedAt = NOW.plusHours(2);

        approval.approve(DIRECTOR, DECISION, new ValidityDays(9), decidedAt);

        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.APPROVED);
        assertThat(approval.getDecidedById()).isEqualTo(DIRECTOR);
        assertThat(approval.getDecidedAt()).isEqualTo(decidedAt);
        assertThat(approval.getDecisionReason()).isEqualTo(DECISION);
        assertThat(approval.getValidUntil()).isEqualTo(decidedAt.plusDays(9));
        assertThat(approval.getStatusChanges()).extracting(CertificateExceptionStatusChange::getEvent)
                .containsExactly(CertificateExceptionEvent.REQUEST, CertificateExceptionEvent.APPROVE);
    }

    @Test
    void requesterCannotApproveOrRejectTheirOwnException() {
        CertificateExceptionApproval approval = pending();

        assertThatThrownBy(() -> approval.approve(REQUESTER, DECISION, SEVEN, NOW)).isInstanceOf(OwnExceptionDecisionException.class);
        assertThatThrownBy(() -> approval.reject(REQUESTER, DECISION, NOW)).isInstanceOf(OwnExceptionDecisionException.class);
        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.PENDING);
        assertThat(approval.getDecidedById()).isNull();
        assertThat(approval.getStatusChanges()).hasSize(1);
    }

    @Test
    void decisionReasonMustBeBetweenTenAndFiveHundredCharacters() {
        assertThatThrownBy(() -> pending().approve(DIRECTOR, "corto", SEVEN, NOW)).isInstanceOf(CertificateExceptionDecisionReasonInvalidException.class);
        assertThatThrownBy(() -> pending().reject(DIRECTOR, null, NOW)).isInstanceOf(CertificateExceptionDecisionReasonInvalidException.class);
        assertThatThrownBy(() -> pending().reject(DIRECTOR, "x".repeat(501), NOW)).isInstanceOf(CertificateExceptionDecisionReasonInvalidException.class);
    }

    @Test
    void rejectAndWithdrawMoveToTheirTerminalStatesWithHistory() {
        CertificateExceptionApproval rejected = pending();
        rejected.reject(DIRECTOR, DECISION, NOW.plusHours(1));
        CertificateExceptionApproval withdrawn = pending();
        withdrawn.withdraw(REQUESTER, NOW.plusHours(1));

        assertThat(rejected.getStatus()).isEqualTo(CertificateExceptionStatus.REJECTED);
        assertThat(rejected.getDecidedById()).isEqualTo(DIRECTOR);
        assertThat(rejected.getValidUntil()).isNull();
        assertThat(withdrawn.getStatus()).isEqualTo(CertificateExceptionStatus.WITHDRAWN);
        assertThat(withdrawn.getStatusChanges()).extracting(CertificateExceptionStatusChange::getEvent)
                .containsExactly(CertificateExceptionEvent.REQUEST, CertificateExceptionEvent.WITHDRAW);
    }

    @Test
    void invalidTransitionsAreRejectedWithoutChangingState() {
        CertificateExceptionApproval approved = approved();
        assertThatThrownBy(() -> approved.approve(DIRECTOR, DECISION, SEVEN, NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
        assertThatThrownBy(() -> approved.reject(DIRECTOR, DECISION, NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
        assertThatThrownBy(() -> approved.withdraw(REQUESTER, NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
        assertThat(approved.getStatus()).isEqualTo(CertificateExceptionStatus.APPROVED);

        CertificateExceptionApproval rejected = pending();
        rejected.reject(DIRECTOR, DECISION, NOW);
        assertThatThrownBy(() -> rejected.approve(DIRECTOR, DECISION, SEVEN, NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
        assertThatThrownBy(() -> rejected.withdraw(REQUESTER, NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
        assertThatThrownBy(() -> rejected.expire(NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
        assertThat(rejected.getStatus()).isEqualTo(CertificateExceptionStatus.REJECTED);
    }

    @Test
    void consumeMarksConsumedRecordsTheCertificateAndHistory() {
        CertificateExceptionApproval approval = approved();
        LocalDateTime consumedAt = NOW.plusDays(1);

        approval.consume(99L, 3L, RegularityKind.PROGRAM, 5L, 30L, consumedAt);

        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.CONSUMED);
        assertThat(approval.getConsumedAt()).isEqualTo(consumedAt);
        assertThat(approval.getConsumedCertificateId()).isEqualTo(99L);
        List<CertificateExceptionStatusChange> history = approval.getStatusChanges();
        assertThat(history.get(history.size() - 1).getEvent()).isEqualTo(CertificateExceptionEvent.CONSUME);
        assertThat(history.get(history.size() - 1).getActorPersonId()).isEqualTo(30L);
    }

    @Test
    void secondConsumeIsRejectedAsAlreadyConsumed() {
        CertificateExceptionApproval approval = approved();
        approval.consume(99L, 3L, RegularityKind.PROGRAM, 5L, 30L, NOW.plusDays(1));

        assertThatThrownBy(() -> approval.consume(100L, 3L, RegularityKind.PROGRAM, 5L, 30L, NOW.plusDays(1))).isInstanceOf(ExceptionAlreadyConsumedException.class);
        assertThat(approval.getConsumedCertificateId()).isEqualTo(99L);
    }

    @Test
    void consumeRequiresApprovedStatusAndTimeInsideValidity() {
        assertThatThrownBy(() -> pending().consume(99L, 3L, RegularityKind.PROGRAM, 5L, 30L, NOW)).isInstanceOf(ExceptionNotApprovedException.class);
        CertificateExceptionApproval rejected = pending();
        rejected.reject(DIRECTOR, DECISION, NOW);
        assertThatThrownBy(() -> rejected.consume(99L, 3L, RegularityKind.PROGRAM, 5L, 30L, NOW)).isInstanceOf(ExceptionNotApprovedException.class);

        CertificateExceptionApproval approval = approved();
        LocalDateTime validUntil = approval.getValidUntil();
        assertThatThrownBy(() -> approval.consume(99L, 3L, RegularityKind.PROGRAM, 5L, 30L, validUntil)).isInstanceOf(ExceptionNotApprovedException.class);
        assertThatThrownBy(() -> approval.consume(99L, 3L, RegularityKind.PROGRAM, 5L, 30L, validUntil.plusSeconds(1))).isInstanceOf(ExceptionNotApprovedException.class);
        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.APPROVED);
        approval.consume(99L, 3L, RegularityKind.PROGRAM, 5L, 30L, validUntil.minusSeconds(1));
        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.CONSUMED);
    }

    @Test
    void validityBoundaryIsExclusiveAndExpireIsDueFromTheBoundary() {
        CertificateExceptionApproval approval = approved();
        LocalDateTime validUntil = approval.getValidUntil();

        assertThat(approval.isValidAt(validUntil.minusSeconds(1))).isTrue();
        assertThat(approval.isValidAt(validUntil)).isFalse();
        assertThat(approval.isDueToExpireAt(validUntil.minusSeconds(1))).isFalse();
        assertThat(approval.isDueToExpireAt(validUntil)).isTrue();
        assertThat(pending().isDueToExpireAt(validUntil.plusDays(30))).isFalse();
    }

    @Test
    void expireMovesApprovedToExpiredAsProcessAndBlocksConsumption() {
        CertificateExceptionApproval approval = approved();

        approval.expire(approval.getValidUntil());

        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.EXPIRED);
        List<CertificateExceptionStatusChange> history = approval.getStatusChanges();
        CertificateExceptionStatusChange last = history.get(history.size() - 1);
        assertThat(last.getActorPersonId()).isNull();
        assertThat(last.getActorProcess()).isEqualTo(CertificateExceptionApproval.EXPIRATION_PROCESS);
        assertThatThrownBy(() -> approval.consume(99L, 3L, RegularityKind.PROGRAM, 5L, 30L, NOW)).isInstanceOf(ExceptionNotApprovedException.class);
    }

    @Test
    void consumeRejectsAnotherStudentKindOrEnrollment() {
        CertificateExceptionApproval approval = approved();

        assertThatThrownBy(() -> approval.consume(99L, 4L, RegularityKind.PROGRAM, 5L, 30L, NOW.plusDays(1)))
                .isInstanceOf(ExceptionTargetMismatchException.class);
        assertThatThrownBy(() -> approval.consume(99L, 3L, RegularityKind.PROGRAM, 6L, 30L, NOW.plusDays(1)))
                .isInstanceOf(ExceptionTargetMismatchException.class);
        assertThatThrownBy(() -> approval.consume(99L, 3L, RegularityKind.COURSE, 5L, 30L, NOW.plusDays(1)))
                .isInstanceOf(ExceptionTargetMismatchException.class);
        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.APPROVED);
    }

    @Test
    void onlyTheRequesterCanWithdraw() {
        CertificateExceptionApproval approval = pending();

        assertThatThrownBy(() -> approval.withdraw(DIRECTOR, NOW)).isInstanceOf(ExceptionWithdrawNotAllowedException.class);
        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.PENDING);
        approval.withdraw(REQUESTER, NOW);
        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.WITHDRAWN);
    }

    private CertificateExceptionApproval directlyCreated() {
        return CertificateExceptionApproval.createApprovedByDirector(RegularityKind.PROGRAM, 7L, 3L, OrganismKind.COURT, "Causa 9/2026", REASON,
                DIRECTOR, SEVEN, NOW);
    }

    @Test
    void directorCreationIsBornApprovedWithValidityAndASingleHistoryEntry() {
        CertificateExceptionApproval approval = directlyCreated();

        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.APPROVED);
        assertThat(approval.isCreatedByDirector()).isTrue();
        assertThat(approval.getRequestedById()).isEqualTo(DIRECTOR);
        assertThat(approval.getDecidedById()).isEqualTo(DIRECTOR);
        assertThat(approval.getDecisionReason()).isEqualTo(CertificateExceptionApproval.DIRECTOR_CREATION_REASON);
        assertThat(approval.getValidUntil()).isEqualTo(NOW.plusDays(7));
        assertThat(approval.getStatusChanges()).hasSize(1);
        assertThat(approval.getStatusChanges().get(0).getEvent()).isEqualTo(CertificateExceptionEvent.DIRECT_APPROVE);
        assertThat(approval.getStatusChanges().get(0).getFromStatus()).isNull();
        assertThat(approval.getStatusChanges().get(0).getToStatus()).isEqualTo(CertificateExceptionStatus.APPROVED);
        assertThat(approval.getStatusChanges().get(0).getReason()).isEqualTo(CertificateExceptionApproval.DIRECTOR_CREATION_REASON);
    }

    @Test
    void directorCreationRequiresAtLeastTwentyCharactersAndAnOrganism() {
        assertThatThrownBy(() -> CertificateExceptionApproval.createApprovedByDirector(RegularityKind.PROGRAM, 7L, 3L, OrganismKind.COURT, "Causa 9",
                "corto", DIRECTOR, SEVEN, NOW)).isInstanceOf(CertificateExceptionReasonInvalidException.class);
        assertThatThrownBy(() -> CertificateExceptionApproval.createApprovedByDirector(RegularityKind.PROGRAM, 7L, 3L, null, "Causa 9", REASON,
                DIRECTOR, SEVEN, NOW)).isInstanceOf(OrganismKindRequiredException.class);
    }

    @Test
    void directlyCreatedExceptionIsSingleUseAndExpiresLikeAnyOther() {
        CertificateExceptionApproval approval = directlyCreated();

        approval.consume(55L, 3L, RegularityKind.PROGRAM, 7L, DIRECTOR, NOW.plusDays(1));

        assertThat(approval.getStatus()).isEqualTo(CertificateExceptionStatus.CONSUMED);
        assertThatThrownBy(() -> approval.consume(56L, 3L, RegularityKind.PROGRAM, 7L, DIRECTOR, NOW.plusDays(2)))
                .isInstanceOf(ExceptionAlreadyConsumedException.class);
        CertificateExceptionApproval other = directlyCreated();
        assertThat(other.isDueToExpireAt(NOW.plusDays(7))).isTrue();
        assertThatThrownBy(() -> other.consume(57L, 3L, RegularityKind.PROGRAM, 7L, DIRECTOR, NOW.plusDays(7)))
                .isInstanceOf(ExceptionNotApprovedException.class);
    }

    @Test
    void directlyCreatedExceptionCanNoLongerBeDecidedOrWithdrawn() {
        CertificateExceptionApproval approval = directlyCreated();

        assertThatThrownBy(() -> approval.approve(30L, DECISION, SEVEN, NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
        assertThatThrownBy(() -> approval.reject(30L, DECISION, NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
        assertThatThrownBy(() -> approval.withdraw(DIRECTOR, NOW)).isInstanceOf(IllegalCertificateExceptionTransitionException.class);
    }
}
