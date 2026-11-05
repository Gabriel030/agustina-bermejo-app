package com.red_escuela.backend.notification;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationRequestDebtBlockedNotificationTest {

    private static GraduationRequestDebtBlockedNotification notification(String amount, int installments, boolean paymentLink) {
        return new GraduationRequestDebtBlockedNotification(5L, new Person(), Money.of(new BigDecimal(amount), Currency.ARS), installments, paymentLink);
    }

    @Test
    void withPaymentLinkTheTextPointsToMyPayments() {
        assertThat(notification("33000", 2, true).message()).isEqualTo(
                "Tu solicitud de egreso está detenida por una deuda pendiente de $33.000,00 (2 cuotas). Regularizala desde Mis pagos: /alumno/pagos");
    }

    @Test
    void withoutPaymentLinkTheTextSendsTheStudentToAdministration() {
        assertThat(notification("1500.50", 1, false).message()).isEqualTo(
                "Tu solicitud de egreso está detenida por una deuda pendiente de $1.500,50 (1 cuota). Acercate a administración para regularizar tu situación.");
    }
}
