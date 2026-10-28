package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PostWithdrawalSummaryPlannedEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class PostWithdrawalSummaryMessageComposer {
    static final String SUBJECT = "Resumen de tu deuda pendiente";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final String INTRO = "Este es el resumen mensual de la deuda pendiente de tus inscripciones dadas de baja.";
    private static final String RECEIPT_LINE = "Si ya realizaste el pago, subí el comprobante desde la sección Pagos de la plataforma.";

    public DeliveryContent composeInApp(PostWithdrawalSummaryPlannedEvent event) {
        StringBuilder text = new StringBuilder(INTRO);
        for (PostWithdrawalSummaryPlannedEvent.Block block : event.blocks()) {
            String lines = block.lines().stream().map(line -> labelOf(line) + " (venció el " + DAY.format(line.dueDate()) + ", " + formatted(line.balance()) + ")")
                    .collect(Collectors.joining("; "));
            text.append(" ").append(lines).append(". Total adeudado en ").append(block.total().currency()).append(": ").append(formatted(block.total())).append(".");
        }
        return DeliveryContent.forInApp(text.toString());
    }

    public DeliveryContent composeEmail(PostWithdrawalSummaryPlannedEvent event) {
        StringBuilder body = new StringBuilder("Hola,\n\n").append(INTRO).append("\n");
        List<PostWithdrawalSummaryPlannedEvent.Block> blocks = event.blocks();
        for (PostWithdrawalSummaryPlannedEvent.Block block : blocks) {
            body.append("\n");
            if (blocks.size() > 1) {
                body.append("Deuda en ").append(block.total().currency()).append(":\n");
            }
            for (PostWithdrawalSummaryPlannedEvent.Line line : block.lines()) {
                body.append("- ").append(labelOf(line)).append(": venció el ").append(DAY.format(line.dueDate())).append(", ").append(formatted(line.balance())).append("\n");
            }
            body.append("Total adeudado en ").append(block.total().currency()).append(": ").append(formatted(block.total())).append("\n");
        }
        body.append("\n").append(RECEIPT_LINE).append("\n");
        return DeliveryContent.forEmail(SUBJECT, body.toString(), null);
    }

    private static String labelOf(PostWithdrawalSummaryPlannedEvent.Line line) {
        String kind = kindLabelOf(line);
        if (line.withdrawnEnrollmentName() == null) {
            return kind;
        }
        String withdrawal = line.withdrawalDate() == null ? "" : " (baja del " + DAY.format(line.withdrawalDate()) + ")";
        return kind + " · " + line.withdrawnEnrollmentName() + withdrawal;
    }

    private static String kindLabelOf(PostWithdrawalSummaryPlannedEvent.Line line) {
        return line.invoiceKind() == PlannedReminderInvoiceKind.ENROLLMENT_FEE ? "Matrícula" : "Cuota mensual";
    }

    private static String formatted(Money money) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ARGENTINA));
        return money.currency() + " " + format.format(money.amount());
    }
}
