package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.MoneyText;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CreditNoteAuthorizationRequestDescriber implements AuthorizationRequestDescriber<IssueCreditNoteCommand> {
    private final InvoiceBriefReader invoiceBriefs;

    public CreditNoteAuthorizationRequestDescriber(InvoiceBriefReader invoiceBriefs) {
        this.invoiceBriefs = invoiceBriefs;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ISSUE_CREDIT_NOTE;
    }

    @Override
    public AuthorizationRequestDescription describe(IssueCreditNoteCommand command, Long authorizationRequestId) {
        Money amount = command.lines().stream().map(CreditNoteLineAmount::amount).reduce(Money::plus).orElse(null);
        InvoiceBriefReader.InvoiceBrief brief = invoiceBriefs.findBriefOf(command.invoiceId()).orElse(null);
        String invoiceLabel = brief == null ? String.valueOf(command.invoiceId()) : brief.label();
        List<DescriptionLine> lines = new ArrayList<>();
        if (brief != null) {
            lines.add(new DescriptionLine("Alumno", brief.studentName()));
        }
        lines.add(new DescriptionLine("Factura", invoiceLabel));
        lines.add(new DescriptionLine("Motivo", CreditNoteReasonLabels.of(command.reason())));
        if (amount != null) {
            lines.add(new DescriptionLine("Importe", MoneyText.of(amount)));
        }
        lines.add(new DescriptionLine("Destino", command.expectedDestination() == CreditNoteDestination.FREE_CREDIT ? "Saldo a favor del alumno" : "Descuenta la deuda de la factura"));
        return new AuthorizationRequestDescription("Nota de crédito sobre " + invoiceLabel, brief == null ? null : brief.studentName(), amount, lines,
                List.of(new EffectLine(EffectTone.SUCCESS, "Se emite la nota de crédito sobre la factura " + invoiceLabel + ".")), DetailBlock.none());
    }
}
