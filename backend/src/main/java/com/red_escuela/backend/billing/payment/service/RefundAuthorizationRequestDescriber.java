package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.RefundPartLine;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.MoneyText;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.operation.IssueRefundCommand;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RefundAuthorizationRequestDescriber implements AuthorizationRequestDescriber<IssueRefundCommand> {
    private final RefundRepository refunds;
    private final PersonNameFinder studentNames;

    public RefundAuthorizationRequestDescriber(RefundRepository refunds, PersonNameFinder studentNames) {
        this.refunds = refunds;
        this.studentNames = studentNames;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ISSUE_REFUND;
    }

    @Override
    public AuthorizationRequestDescription describe(IssueRefundCommand command, Long authorizationRequestId) {
        List<Refund> parts = refunds.findByAuthorizationRequestIdOrderByIdAsc(authorizationRequestId);
        String studentName = studentNames.findFullNamesByStudentId(List.of(command.studentId())).get(command.studentId());
        long payments = parts.stream().map(Refund::getPaymentId).distinct().count();
        String requested = command.amount() == null ? "saldo a favor" : MoneyText.of(command.amount());
        List<DescriptionLine> lines = new ArrayList<>();
        if (studentName != null) {
            lines.add(new DescriptionLine("Alumno", studentName));
        }
        lines.add(new DescriptionLine("Pedido", "Reembolso de " + requested));
        lines.add(new DescriptionLine("Destino", destinationOf(command)));
        List<EffectLine> effects = List.of(
                new EffectLine(EffectTone.SUCCESS, parts.size() == 1 ? "La parte pasa a Aprobado y queda lista para transferir."
                        : "Las " + parts.size() + " partes pasan a Aprobado y quedan listas para transferir."),
                new EffectLine(EffectTone.NEUTRAL, "La plata ya está reservada desde el pedido: el saldo disponible no cambia."),
                new EffectLine(EffectTone.NEUTRAL, "Si se rechaza, " + requested + " vuelve a estar disponible para el alumno."));
        return new AuthorizationRequestDescription(payments == 0 ? "Devolver saldo a favor" : "Devolver saldo a favor, " + payments + (payments == 1 ? " pago" : " pagos"),
                studentName, command.amount(), lines, effects, DetailBlock.refundParts(parts.stream().map(RefundAuthorizationRequestDescriber::partLineOf).toList()));
    }

    private static String destinationOf(IssueRefundCommand command) {
        boolean hasAccount = command.destinationAccount() != null && !command.destinationAccount().isBlank();
        boolean hasHolder = command.destinationHolder() != null && !command.destinationHolder().isBlank();
        if (!hasAccount && !hasHolder) {
            return "El de cada pago original";
        }
        return (hasAccount ? command.destinationAccount().strip() : "") + (hasAccount && hasHolder ? " · " : "") + (hasHolder ? command.destinationHolder().strip() : "");
    }

    private static RefundPartLine partLineOf(Refund refund) {
        return new RefundPartLine(refund.getId(), refund.getPaymentId(), refund.getMethodType().name(), refund.getStatus().name(), refund.getAmount().value(),
                refund.getDestinationAccount(), refund.getDestinationHolder());
    }
}
