package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-287")
class CohortEntryRegistrationTest {

    private static final Instant NOW = Instant.parse("2027-03-10T10:00:00Z");
    private static final StatusChangeActor PROCESS = StatusChangeActor.ofProcess("INVOICE_ISSUING");
    private static final Percentage THIRTY_PERCENT = Percentage.ofPoints(new BigDecimal("30"));

    private static CohortEntryRegistration registrationBy(StatusChangeActor actor) {
        return new CohortEntryRegistration(EnrollmentReference.ofProgram(7L), 3L, Currency.ARS, 11L, THIRTY_PERCENT, YearMonth.of(2027, 3), actor, "Descuento de ingreso");
    }

    @Test
    void aRegisteredEntryDiscountIsBornExhaustedWithASingleUseOnTheEnrollmentFee() {
        StudentDiscount discount = StudentDiscount.registerCohortEntryDiscount(registrationBy(PROCESS), NOW);

        assertThat(discount.getStatus()).isEqualTo(StudentDiscountStatus.EXHAUSTED);
        assertThat(discount.getOrigin()).isEqualTo(StudentDiscountOrigin.COHORT_ENTRY_CONFIGURATION);
        assertThat(discount.getDiscountType()).isEqualTo(DiscountType.COHORT_ENTRY);
        assertThat(discount.getApplicableInvoiceType()).isEqualTo(InvoiceType.ENROLLMENT_FEE);
        assertThat(discount.getInstallmentsTotal()).isEqualTo(1);
        assertThat(discount.getCalculation()).isEqualTo(new MonetaryAdjustment.Proportional(THIRTY_PERCENT));
        assertThat(discount.getConfigurationVersionId()).isEqualTo(11L);
        assertThat(discount.getDiscountDefinitionId()).isNull();
        assertThat(discount.getProgramEnrollmentId()).isEqualTo(7L);
        assertThat(discount.getCourseEnrollmentId()).isNull();
        assertThat(discount.getGrantedById()).isNull();
        assertThat(discount.getFromPeriod()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(discount.getToPeriod()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(discount.getGrantedAt()).isEqualTo(NOW);
        assertThat(discount.getStatusChanges()).extracting(StudentDiscountStatusChange::getEvent)
                .containsExactly(StudentDiscountEvent.DISCOUNT_GRANTED, StudentDiscountEvent.LAST_INSTALLMENT_CONSUMED);
    }

    @Test
    void aPersonActingAsGrantorIsRecordedAsTheGrantor() {
        StudentDiscount discount = StudentDiscount.registerCohortEntryDiscount(registrationBy(StatusChangeActor.ofPerson(5L)), NOW);

        assertThat(discount.getGrantedById()).isEqualTo(5L);
    }

    @Test
    void aRegistrationWithoutConfigurationVersionIsRejected() {
        assertThatThrownBy(() -> new CohortEntryRegistration(EnrollmentReference.ofProgram(7L), 3L, Currency.ARS, null, THIRTY_PERCENT, YearMonth.of(2027, 3), PROCESS, "x"))
                .isInstanceOf(NullPointerException.class);
    }
}
