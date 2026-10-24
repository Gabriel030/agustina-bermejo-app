package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.operation.AuthorizationRequestObserver;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundLimits;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.billing.payment.operation.IssueRefundCommand;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class RefundRequestAuthorizationObserver implements AuthorizationRequestObserver {
    private static final Logger log = LoggerFactory.getLogger(RefundRequestAuthorizationObserver.class);
    private static final String EXPIRATION_PROCESS = "REFUND_REQUEST_EXPIRATION";
    private static final String WITHDRAWN_REASON = "El pedido de devolución fue retirado.";
    private static final String EXPIRED_REASON = "El pedido de devolución venció sin respuesta del director.";

    private final RefundRequestRecorder recorder;
    private final RefundRepository refunds;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final Clock clock;

    public RefundRequestAuthorizationObserver(RefundRequestRecorder recorder, RefundRepository refunds, StudentCurrentAccountService accountService,
                                              CurrentAccountAllocationService allocationService, Clock clock) {
        this.recorder = recorder;
        this.refunds = refunds;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.clock = clock;
    }

    @Override
    public void afterOpened(Long requestId, BillingOperationType type, Object context) {
        if (type == BillingOperationType.ISSUE_REFUND && context instanceof IssueRefundCommand command) {
            List<Refund> parts = recorder.recordRequestedPartsOf(command.studentId(), command.amount(), command.requestedDestination(), command.requestedByPersonId(), requestId);
            log.info("Devolución pedida: authorizationRequestId={}, pedido={}, partes={}, alumno={}", requestId, parts.get(0).getRefundRequestId(), parts.size(),
                    command.studentId());
        }
    }

    @Override
    public void afterRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        rejectRequestedPartsOf(requestId, type, StatusChangeActor.ofPerson(deciderId), reason);
    }

    @Override
    public void afterWithdrawn(Long requestId, BillingOperationType type, Long requesterId) {
        rejectRequestedPartsOf(requestId, type, StatusChangeActor.ofPerson(requesterId), WITHDRAWN_REASON);
    }

    @Override
    public void afterExpired(Long requestId, BillingOperationType type) {
        rejectRequestedPartsOf(requestId, type, StatusChangeActor.ofProcess(EXPIRATION_PROCESS), EXPIRED_REASON);
    }

    @Override
    public void afterResumeRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        rejectRequestedPartsOf(requestId, type, StatusChangeActor.ofPerson(deciderId), reason);
    }

    private void rejectRequestedPartsOf(Long requestId, BillingOperationType type, StatusChangeActor actor, String reason) {
        if (type != BillingOperationType.ISSUE_REFUND) {
            return;
        }
        String storedReason = abbreviated(reason);
        List<Refund> requested = refunds.findByAuthorizationRequestIdAndStatusOrderByIdAsc(requestId, RefundStatus.REQUESTED);
        requested.forEach(refund -> {
            refund.reject(storedReason, actor);
            refunds.save(refund);
        });
        requested.stream().map(Refund::getStudentCurrentAccountId).distinct().sorted().forEach(accountId -> {
            StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
            int allocations = allocationService.allocateAccountCreditToOldestInvoices(account, LocalDate.now(clock), actor).allocations().size();
            log.info("Saldo a favor liberado y imputado al cerrarse el pedido de devolución: authorizationRequestId={}, cuenta={}, imputaciones={}", requestId, accountId, allocations);
        });
        log.info("Devolución cerrada sin efecto: authorizationRequestId={}, partes={}, actor={}", requestId, requested.size(), actor);
    }

    private String abbreviated(String reason) {
        String text = reason == null || reason.isBlank() ? "Sin motivo informado." : reason.strip();
        return text.length() <= RefundLimits.MAX_REJECTION_REASON_LENGTH ? text : text.substring(0, RefundLimits.MAX_REJECTION_REASON_LENGTH);
    }
}
