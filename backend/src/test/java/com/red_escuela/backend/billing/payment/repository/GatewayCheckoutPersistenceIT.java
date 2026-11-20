package com.red_escuela.backend.billing.payment.repository;

import com.red_escuela.backend.billing.operation.GatewayCheckoutRecording;
import com.red_escuela.backend.billing.operation.NewGatewayCheckout;
import com.red_escuela.backend.billing.payment.domain.GatewayCheckout;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-538")
class GatewayCheckoutPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private GatewayCheckoutRepository repository;
    @Autowired
    private GatewayCheckoutRecording recording;
    @Autowired
    private JdbcTemplate jdbc;

    private long persistedInvoice() {
        Long addressId = jdbc.queryForObject("insert into address default values returning id", Long.class);
        String username = "checkout" + System.nanoTime();
        Long personId = jdbc.queryForObject("insert into person (username, email, address_id, status) values (?, ?, ?, 'ACTIVE') returning id",
                Long.class, username, username + "@example.com", addressId);
        Long institutionId = jdbc.queryForObject("insert into institution (name) values (?) returning id", Long.class, "Inst " + System.nanoTime());
        Long studentId = jdbc.queryForObject("insert into student (person_id, institution_id, onboarding_status) values (?, ?, 'CONFIRMADO') returning id",
                Long.class, personId, institutionId);
        Long accountId = jdbc.queryForObject("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at)
                values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now()) returning id
                """, Long.class, studentId);
        return jdbc.queryForObject("""
                insert into invoice (student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status,
                    payment_status, invoice_number, issue_date, due_date, student_snapshot, lines_total, lines_total_currency,
                    discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency,
                    credited_amount, credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at)
                values (?, 'ARS', 'INVOICE', 'MONTHLY_INSTALLMENT', 'MONTHLY_RUN', 'ISSUED', 'PENDING', ?, current_date, current_date + 10,
                    cast('{}' as jsonb), 100, 'ARS', 0, 'ARS', 100, 'ARS', 0, 'ARS', 0, 'ARS', 100, 'ARS', now(), now()) returning id
                """, Long.class, accountId, "F-" + String.format("%08d", System.nanoTime() % 100_000_000L));
    }

    @Test
    void checkoutIsStoredAndReadBackWithAmountCurrencyAndExpiry() {
        long invoiceId = persistedInvoice();
        UUID reference = UUID.randomUUID();

        recording.recordMercadoPagoCheckout(new NewGatewayCheckout(reference, invoiceId,
                NonNegativeMoney.of(new BigDecimal("1500.50"), Currency.ARS), "pref-it"));

        GatewayCheckout stored = repository.findByPublicReference(reference).orElseThrow();
        assertThat(stored.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(stored.getGateway()).isEqualTo(PaymentGatewayCode.MERCADO_PAGO);
        assertThat(stored.getAmount().value().amount()).isEqualByComparingTo("1500.50");
        assertThat(stored.getAmount().value().currency()).isEqualTo(Currency.ARS);
        assertThat(stored.getGatewayPreferenceId()).isEqualTo("pref-it");
        assertThat(Duration.between(stored.getCreatedAt(), stored.getExpiresAt())).isEqualTo(Duration.ofHours(48));
        assertThat(repository.findByInvoiceIdOrderByIdAsc(invoiceId)).extracting(GatewayCheckout::getPublicReference).containsExactly(reference);
    }
}
