package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.util.Objects;
import java.util.Set;

public record RegisterGatewayPaymentCommand(Long studentCurrentAccountId, Long reportedInvoiceId, Set<Long> programAdmissionInvoiceIds, Money amount,
                                            PaymentGatewayCode gateway, CardPaymentDetails details, String gatewayPaymentId, GatewayRawStatus rawStatus,
                                            StatusChangeActor actor) {

    public RegisterGatewayPaymentCommand {
        Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        Objects.requireNonNull(reportedInvoiceId, "reportedInvoiceId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(gateway, "gateway");
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(gatewayPaymentId, "gatewayPaymentId");
        Objects.requireNonNull(actor, "actor");
        programAdmissionInvoiceIds = Set.copyOf(programAdmissionInvoiceIds);
    }
}
