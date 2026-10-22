package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PlannedReminderBlock;
import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class CollectionReminderMessageComposer {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final String PAYMENT_ORDER_LINE = "Los pagos se aplican primero a lo más vencido.";
    private static final String RECEIPT_LINE = "Si ya realizaste el pago, subí el comprobante desde la plataforma.";

    public DeliveryContent composeInApp(PlannedReminderMessage message, LocalDate businessDate) {
        StringBuilder text = new StringBuilder(introOf(message.leadNoticeType()));
        List<PlannedReminderLine> highlighted = message.lines().stream().filter(PlannedReminderLine::isHighlighted).toList();
        String summary = highlighted.stream()
                .map(line -> kindLabelOf(line) + " (" + dueTextOf(line, businessDate) + ", " + formatted(line.balance()) + ")")
                .collect(Collectors.joining("; "));
        text.append(" ").append(summary).append(".");
        message.blocks().forEach(block -> text.append(" Total adeudado en ").append(block.currency()).append(": ").append(formatted(block.total())).append("."));
        return DeliveryContent.forInApp(text.toString());
    }

    public DeliveryContent composeEmail(PlannedReminderMessage message, LocalDate businessDate) {
        StringBuilder body = new StringBuilder("Hola,\n\n").append(introOf(message.leadNoticeType())).append("\n");
        boolean severalBlocks = message.blocks().size() > 1;
        for (PlannedReminderBlock block : message.blocks()) {
            body.append("\n");
            if (severalBlocks) {
                body.append("Deuda en ").append(block.currency()).append(":\n");
            }
            appendBlock(body, message, block, businessDate);
        }
        body.append("\n").append(PAYMENT_ORDER_LINE).append("\n").append(RECEIPT_LINE).append("\n");
        return DeliveryContent.forEmail(subjectOf(message.leadNoticeType()), body.toString(), null);
    }

    public String subjectOf(PlannedReminderNoticeType leadNoticeType) {
        return switch (leadNoticeType) {
            case ISSUANCE_NOTICE -> "Se emitió una cuota nueva en tu cuenta";
            case AFTER_ISSUE_REMINDER -> "Recordatorio de tu cuota pendiente";
            case BEFORE_DUE_REMINDER -> "Se acerca el vencimiento de tu cuota";
            case AFTER_DUE_REMINDER -> "Tenés una cuota vencida";
            case BEFORE_SUSPENSION_REMINDER -> "Tenés cuotas impagas en tu inscripción";
        };
    }

    private String introOf(PlannedReminderNoticeType leadNoticeType) {
        return switch (leadNoticeType) {
            case ISSUANCE_NOTICE -> "Se emitió una cuota nueva en tu cuenta.";
            case AFTER_ISSUE_REMINDER -> "Te recordamos que tenés una cuota emitida pendiente de pago.";
            case BEFORE_DUE_REMINDER -> "Se acerca el vencimiento de una de tus cuotas.";
            case AFTER_DUE_REMINDER -> "Tenés una cuota vencida.";
            case BEFORE_SUSPENSION_REMINDER -> "Tenés cuotas impagas en tu inscripción. Regularizar tu situación te permite mantenerla al día.";
        };
    }

    private void appendBlock(StringBuilder body, PlannedReminderMessage message, PlannedReminderBlock block, LocalDate businessDate) {
        boolean marksHighlighted = message.format() == PlannedReminderFormat.DAILY_DIGEST;
        for (PlannedReminderLine line : block.lines()) {
            body.append(line.debitNote() ? "    - " : "- ").append(kindLabelOf(line)).append(": ").append(dueTextOf(line, businessDate)).append(", ")
                    .append(formatted(line.balance()));
            if (line.receiptUnderReview()) {
                body.append(" (comprobante en revisión)");
            }
            if (marksHighlighted && line.isHighlighted()) {
                body.append(" (aviso de hoy)");
            }
            body.append("\n");
        }
        body.append("Total adeudado en ").append(block.currency()).append(": ").append(formatted(block.total())).append("\n");
        if (!block.availableCredit().isZero()) {
            body.append("Tenés ").append(formatted(block.availableCredit())).append(" a favor.\n");
        }
        if (block.hasCreditShortfall()) {
            body.append("Te faltan ").append(formatted(block.missingForNext())).append(" para cubrir ").append(shortfallTargetOf(block, businessDate)).append(".\n");
        }
    }

    private String shortfallTargetOf(PlannedReminderBlock block, LocalDate businessDate) {
        Optional<PlannedReminderLine> target = block.lines().stream().filter(line -> block.shortfallInvoiceId().equals(line.invoiceId())).findFirst();
        return target.map(line -> "la factura de " + kindLabelOf(line).toLowerCase(ARGENTINA) + " (" + dueTextOf(line, businessDate) + ")").orElse("tu próxima factura");
    }

    private String kindLabelOf(PlannedReminderLine line) {
        return switch (line.invoiceKind()) {
            case MONTHLY_INSTALLMENT -> "Cuota mensual";
            case ENROLLMENT_FEE -> "Matrícula";
            case LATE_FEE -> "Recargo por mora";
        };
    }

    private String dueTextOf(PlannedReminderLine line, LocalDate businessDate) {
        return (line.dueDate().isBefore(businessDate) ? "venció el " : "vence el ") + DAY.format(line.dueDate());
    }

    private String formatted(Money money) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ARGENTINA));
        return money.currency() + " " + format.format(money.amount());
    }
}
