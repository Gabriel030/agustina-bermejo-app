package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.domain.CancelInvoicesAfterWithdrawalCommand;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationItem;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationKind;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class WithdrawalCancellationAuthorizationRequestDescriber implements AuthorizationRequestDescriber<CancelInvoicesAfterWithdrawalCommand> {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceBriefReader invoiceBriefs;

    public WithdrawalCancellationAuthorizationRequestDescriber(InvoiceBriefReader invoiceBriefs) {
        this.invoiceBriefs = invoiceBriefs;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CANCEL_INVOICES_AFTER_WITHDRAWAL;
    }

    @Override
    public AuthorizationRequestDescription describe(CancelInvoicesAfterWithdrawalCommand command, Long authorizationRequestId) {
        long cancelled = command.items().stream().filter(item -> item.kind() == WithdrawalCancellationKind.CANCEL_INVOICE).count();
        long reduced = command.items().size() - cancelled;
        String studentName = invoiceBriefs.findBriefOf(command.items().get(0).invoiceId()).map(InvoiceBriefReader.InvoiceBrief::studentName).orElse(null);
        List<DescriptionLine> lines = new ArrayList<>();
        if (studentName != null) {
            lines.add(new DescriptionLine("Alumno", studentName));
        }
        lines.add(new DescriptionLine("Baja", "Fecha de baja " + DAY.format(command.withdrawalDate())));
        lines.add(new DescriptionLine("Facturas", cancelled + " a cancelar, " + reduced + " a reducir"));
        List<EffectLine> effects = List.of(new EffectLine(EffectTone.SUCCESS, "Se ajustan con notas de crédito: " + countOf(command.items()) + " posteriores a la baja."));
        return new AuthorizationRequestDescription("Baja del " + DAY.format(command.withdrawalDate()) + ": " + countOf(command.items()), studentName, null, lines, effects,
                DetailBlock.none());
    }

    private static String countOf(List<WithdrawalCancellationItem> items) {
        return items.size() == 1 ? "1 factura" : items.size() + " facturas";
    }
}
