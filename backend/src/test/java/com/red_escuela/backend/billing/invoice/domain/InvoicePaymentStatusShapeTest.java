package com.red_escuela.backend.billing.invoice.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-136")
@Tag("D-165")
@Tag("D-142")
class InvoicePaymentStatusShapeTest {

    @Test
    void theEnumsHaveNoPartialPaymentNoWaiverAndNoWithdrawalEvent() {
        assertThat(Arrays.stream(InvoicePaymentStatus.values()).map(Enum::name))
                .containsExactly("PENDING", "PAYMENT_UNDER_REVIEW", "PAID", "OVERDUE", "CANCELLED");
        assertThat(Arrays.stream(InvoicePaymentEvent.values()).map(Enum::name))
                .noneMatch(name -> name.contains("WAIV") || name.contains("PARTIAL_PAYMENT") || name.contains("WITHDRAW")
                        || name.contains("DROP") || name.contains("CONDON"))
                .hasSize(13);
    }
}
