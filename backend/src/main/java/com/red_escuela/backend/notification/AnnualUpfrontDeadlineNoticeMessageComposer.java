package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.AnnualUpfrontDeadlineNoticePlannedEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class AnnualUpfrontDeadlineNoticeMessageComposer {
    static final String SUBJECT = "Se acerca el plazo de pago de tu pedido del año";
    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final String HOW_TO_PAY_LINE = "Para pagarlo, andá a la sección Pagos de la plataforma y pagá, o subí el comprobante de tu transferencia, antes de ese momento.";
    private static final String EXPIRY_LINE = "Si el plazo vence sin que hayas iniciado el pago, el pedido se descarta y seguís con las cuotas mensuales.";

    public DeliveryContent composeInApp(AnnualUpfrontDeadlineNoticePlannedEvent event) {
        return DeliveryContent.forInApp(introOf(event) + " " + HOW_TO_PAY_LINE + " " + EXPIRY_LINE);
    }

    public DeliveryContent composeEmail(AnnualUpfrontDeadlineNoticePlannedEvent event) {
        String body = "Hola,\n\n" + introOf(event) + "\n\n" + HOW_TO_PAY_LINE + "\n" + EXPIRY_LINE + "\n";
        return DeliveryContent.forEmail(SUBJECT, body, null);
    }

    private static String introOf(AnnualUpfrontDeadlineNoticePlannedEvent event) {
        return "Tu pedido de pago anual por " + formatted(event.total()) + " vence el " + MOMENT.format(event.paymentDeadline()) + " y todavía no iniciaste el pago.";
    }

    private static String formatted(Money money) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ARGENTINA));
        return money.currency() + " " + format.format(money.amount());
    }
}
