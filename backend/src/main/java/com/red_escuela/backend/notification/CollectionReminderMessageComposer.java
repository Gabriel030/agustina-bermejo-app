package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PlannedReminderBlock;
import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PlannedReminderLateFee;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.billing.events.PlannedReminderSuspensionRule;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
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
        StringBuilder text = new StringBuilder(introOf(message));
        List<PlannedReminderLine> described = message.format() == PlannedReminderFormat.DEBT_SUMMARY ? message.lines() : message.lines().stream().filter(PlannedReminderLine::isHighlighted).toList();
        String summary = described.stream()
                .map(line -> kindLabelOf(line) + " (" + dueTextOf(line, businessDate) + ", " + formatted(line.balance()) + ")")
                .collect(Collectors.joining("; "));
        text.append(" ").append(summary).append(".");
        message.blocks().forEach(block -> text.append(" Total adeudado en ").append(block.currency()).append(": ").append(formatted(block.total())).append("."));
        return DeliveryContent.forInApp(text.toString());
    }

    public DeliveryContent composeEmail(PlannedReminderMessage message, LocalDate businessDate) {
        StringBuilder body = new StringBuilder("Hola,\n\n").append(introOf(message)).append("\n");
        boolean severalBlocks = message.blocks().size() > 1;
        for (PlannedReminderBlock block : message.blocks()) {
            body.append("\n");
            if (severalBlocks) {
                body.append("Deuda en ").append(block.currency()).append(":\n");
            }
            appendBlock(body, message, block, businessDate);
        }
        appendNewInstallmentMention(body, message, businessDate);
        appendLateFeeLines(body, message, businessDate);
        appendSuspensionLine(body, message);
        body.append("\n").append(PAYMENT_ORDER_LINE).append("\n").append(RECEIPT_LINE).append("\n");
        return DeliveryContent.forEmail(subjectOf(message), body.toString(), null);
    }

    public String subjectOf(PlannedReminderMessage message) {
        return message.format() == PlannedReminderFormat.DEBT_SUMMARY ? "Resumen de tu deuda" : subjectOf(message.leadNoticeType());
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

    private String introOf(PlannedReminderMessage message) {
        return message.format() == PlannedReminderFormat.DEBT_SUMMARY ? "Este es el resumen de tu deuda." : introOf(message.leadNoticeType());
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
        boolean marksHighlighted = message.format() != PlannedReminderFormat.OWN_NOTICE;
        for (PlannedReminderLine line : block.lines()) {
            body.append(line.debitNote() ? "    - " : "- ").append(kindLabelOf(line)).append(": ").append(dueTextOf(line, businessDate)).append(", ")
                    .append(formatted(line.balance()));
            if (line.receiptUnderReview()) {
                body.append(" (comprobante en revisión)");
            }
            if (marksHighlighted && line.isHighlighted()) {
                body.append(isNewInstallment(line) ? " (cuota nueva)" : " (aviso de hoy)");
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

    private boolean isNewInstallment(PlannedReminderLine line) {
        return line.notices().stream().anyMatch(notice -> notice.noticeType() == PlannedReminderNoticeType.ISSUANCE_NOTICE);
    }

    private void appendNewInstallmentMention(StringBuilder body, PlannedReminderMessage message, LocalDate businessDate) {
        if (message.format() != PlannedReminderFormat.DEBT_SUMMARY) {
            return;
        }
        for (PlannedReminderLine line : message.lines()) {
            if (isNewInstallment(line)) {
                body.append("\nAdemás, se emitió una cuota nueva de ").append(kindLabelOf(line).toLowerCase(ARGENTINA)).append(" que ")
                        .append(dueTextOf(line, businessDate)).append(".\n");
            }
        }
    }

    private void appendLateFeeLines(StringBuilder body, PlannedReminderMessage message, LocalDate businessDate) {
        boolean describesEveryLine = message.format() == PlannedReminderFormat.DEBT_SUMMARY;
        List<PlannedReminderLine> described = message.lines().stream()
                .filter(line -> line.lateFee() != null && !line.debitNote() && line.invoiceKind() != PlannedReminderInvoiceKind.LATE_FEE)
                .filter(line -> describesEveryLine || line.isHighlighted())
                .toList();
        for (PlannedReminderLine line : described) {
            body.append("\n").append(lateFeeSentenceOf(line, businessDate)).append("\n");
        }
    }

    private String lateFeeSentenceOf(PlannedReminderLine line, LocalDate businessDate) {
        PlannedReminderLateFee lateFee = line.lateFee();
        LocalDate graceEnd = line.dueDate().plusDays(lateFee.graceDays());
        String charge = "un recargo de " + ratePointsOf(lateFee.dailyRate()) + " % por día desde el " + DAY.format(line.dueDate())
                + " (" + formatted(lateFee.finalPrice().times(lateFee.dailyRate())) + " por día)";
        String lead = kindLabelOf(line) + " con vencimiento el " + DAY.format(line.dueDate()) + ": ";
        return businessDate.isAfter(graceEnd) ? lead + "corre " + charge + "." : lead + "si se paga después del " + DAY.format(graceEnd) + ", corre " + charge + ".";
    }

    private String ratePointsOf(Percentage rate) {
        return new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(ARGENTINA)).format(rate.points());
    }

    private void appendSuspensionLine(StringBuilder body, PlannedReminderMessage message) {
        PlannedReminderSuspensionRule rule = message.suspensionRule();
        if (rule == null) {
            return;
        }
        int invoiceCount = Math.max(1, rule.afterInvoiceCount());
        body.append("\nSegún la regla vigente, la inscripción puede suspenderse si hay ").append(invoiceCount).append(invoiceCount == 1 ? " factura vencida" : " facturas vencidas")
                .append(" o más, o si pasan más de ").append(rule.afterDays()).append(rule.afterDays() == 1 ? " día" : " días")
                .append(" desde el vencimiento de la factura vencida más antigua.\n");
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
