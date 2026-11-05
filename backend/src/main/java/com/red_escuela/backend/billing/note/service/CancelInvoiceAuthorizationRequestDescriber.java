package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.MoneyText;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CancelInvoiceAuthorizationRequestDescriber implements AuthorizationRequestDescriber<CancelInvoiceCommand> {
    private final InvoiceBriefReader invoiceBriefs;

    public CancelInvoiceAuthorizationRequestDescriber(InvoiceBriefReader invoiceBriefs) {
        this.invoiceBriefs = invoiceBriefs;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CANCEL_INVOICE;
    }

    @Override
    public AuthorizationRequestDescription describe(CancelInvoiceCommand command, Long authorizationRequestId) {
        return invoiceBriefs.findBriefOf(command.invoiceId()).map(brief -> describeExisting(command, brief)).orElseGet(() -> describeMissing(command));
    }

    private AuthorizationRequestDescription describeExisting(CancelInvoiceCommand command, InvoiceBriefReader.InvoiceBrief brief) {
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(new DescriptionLine("Alumno", brief.studentName()));
        lines.add(new DescriptionLine("Factura", brief.label()));
        lines.add(new DescriptionLine("Importe", MoneyText.of(brief.total())));
        lines.add(new DescriptionLine("Motivo", CreditNoteReasonLabels.of(command.reason())));
        return new AuthorizationRequestDescription("Cancelar la factura " + brief.label(), brief.studentName(), brief.total(), lines,
                List.of(new EffectLine(EffectTone.SUCCESS, "Se emite la nota de crédito y la factura queda cancelada.")), DetailBlock.none());
    }

    private AuthorizationRequestDescription describeMissing(CancelInvoiceCommand command) {
        return new AuthorizationRequestDescription("Cancelar la factura " + command.invoiceId(), null, null,
                List.of(new DescriptionLine("Motivo", CreditNoteReasonLabels.of(command.reason()))), List.of(), DetailBlock.none());
    }
}
