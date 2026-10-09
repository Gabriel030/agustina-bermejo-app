package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-094")
class PaymentAllocationTest {

    private static final LocalDate DAY = LocalDate.of(2027, 4, 20);
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess("TEST");

    private PaymentAllocation allocation() {
        Invoice invoice = AccountTestData.issuedInvoice(10L, "80000.00");
        PaymentAllocation allocation = PaymentAllocation.recordFullInvoiceAllocation(invoice, ars("80000.00"), DAY.minusDays(3), DAY, ACTOR);
        ReflectionTestUtils.setField(allocation, "id", 55L);
        return allocation;
    }

    @Test
    void anAllocationKeepsTheInvoiceAccountAmountAndDates() {
        PaymentAllocation allocation = allocation();

        assertThat(allocation.getInvoiceId()).isEqualTo(10L);
        assertThat(allocation.getStudentCurrentAccountId()).isEqualTo(AccountTestData.ACCOUNT_ID);
        assertThat(allocation.getAmount()).isEqualTo(ars("80000.00"));
        assertThat(allocation.getEffectiveDate()).isEqualTo(DAY.minusDays(3));
        assertThat(allocation.getAllocatedOn()).isEqualTo(DAY);
        assertThat(allocation.getActorProcess()).isEqualTo("TEST");
        assertThat(allocation.getActorPersonId()).isNull();
        assertThat(allocation.isReversal()).isFalse();
        assertThat(allocation.getRecordedAt()).isNotNull();
    }

    @Test
    void anAllocationOfZeroIsRejected() {
        Invoice invoice = AccountTestData.issuedInvoice(10L, "80000.00");

        assertThatThrownBy(() -> PaymentAllocation.recordFullInvoiceAllocation(invoice, ars("0.00"), DAY, DAY, ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAllocationOfAnInvoiceWithoutIdIsRejected() {
        Invoice draft = AccountTestData.draftInvoice(10L, "80000.00");
        ReflectionTestUtils.setField(draft, "id", null);

        assertThatThrownBy(() -> PaymentAllocation.recordFullInvoiceAllocation(draft, ars("80000.00"), DAY, DAY, ACTOR)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aReversalIsANewRowThatPointsToTheOriginalWithTheSameInvoiceAndAmount() {
        PaymentAllocation allocation = allocation();

        PaymentAllocation reversal = allocation.recordAllocationReversal(DAY.plusDays(1), "Contracargo", StatusChangeActor.ofPerson(5L));

        assertThat(reversal.isReversal()).isTrue();
        assertThat(reversal.getReversesAllocationId()).isEqualTo(55L);
        assertThat(reversal.getInvoiceId()).isEqualTo(allocation.getInvoiceId());
        assertThat(reversal.getAmount()).isEqualTo(allocation.getAmount());
        assertThat(reversal.getAllocatedOn()).isEqualTo(DAY.plusDays(1));
        assertThat(reversal.getEffectiveDate()).isEqualTo(allocation.getEffectiveDate());
        assertThat(reversal.getReason()).isEqualTo("Contracargo");
        assertThat(reversal.getActorPersonId()).isEqualTo(5L);
        assertThat(reversal.getActorProcess()).isNull();
        assertThat(allocation.isReversal()).isFalse();
    }

    @Test
    void aReversalNeedsAReason() {
        PaymentAllocation allocation = allocation();

        assertThatThrownBy(() -> allocation.recordAllocationReversal(DAY, " ", ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> allocation.recordAllocationReversal(DAY, null, ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> allocation.recordAllocationReversal(DAY, "x".repeat(501), ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThat(allocation.recordAllocationReversal(DAY, "x".repeat(500), ACTOR).getReason()).hasSize(500);
    }

    @Test
    void aReversalCannotBeReversedAndAnUnsavedAllocationCannotBeReversed() {
        PaymentAllocation reversal = allocation().recordAllocationReversal(DAY, "Contracargo", ACTOR);
        ReflectionTestUtils.setField(reversal, "id", 56L);
        PaymentAllocation unsaved = allocation();
        ReflectionTestUtils.setField(unsaved, "id", null);

        assertThatThrownBy(() -> reversal.recordAllocationReversal(DAY, "Otra", ACTOR)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> unsaved.recordAllocationReversal(DAY, "Otra", ACTOR)).isInstanceOf(NullPointerException.class);
    }
}
