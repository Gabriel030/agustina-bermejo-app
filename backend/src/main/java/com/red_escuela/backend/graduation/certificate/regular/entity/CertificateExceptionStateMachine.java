package com.red_escuela.backend.graduation.certificate.regular.entity;

import com.red_escuela.backend.graduation.certificate.regular.exception.IllegalCertificateExceptionTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import java.util.Optional;

import static com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionEvent.*;
import static com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus.*;

public final class CertificateExceptionStateMachine {

    public static final StateTransitionTable<CertificateExceptionStatus, CertificateExceptionEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(CertificateExceptionStatus.class, CertificateExceptionEvent.class)
                    .allowInitialTransition(REQUEST, PENDING)
                    .allowTransition(PENDING, APPROVE, APPROVED)
                    .allowTransition(PENDING, REJECT, REJECTED)
                    .allowTransition(PENDING, WITHDRAW, WITHDRAWN)
                    .allowTransition(APPROVED, CONSUME, CONSUMED)
                    .allowTransition(APPROVED, EXPIRE, EXPIRED)
                    .buildTransitionTable();

    private CertificateExceptionStateMachine() {
    }

    public static Optional<CertificateExceptionStatus> findTargetStateFor(CertificateExceptionStatus from, CertificateExceptionEvent event) {
        return TRANSITIONS.findTargetStateFor(from, event);
    }

    public static CertificateExceptionStatus requireTargetFor(CertificateExceptionStatus from, CertificateExceptionEvent event) {
        return findTargetStateFor(from, event).orElseThrow(() -> new IllegalCertificateExceptionTransitionException(from, event));
    }
}
