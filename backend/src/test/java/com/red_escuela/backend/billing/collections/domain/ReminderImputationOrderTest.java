package com.red_escuela.backend.billing.collections.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderImputationOrderTest {

    @Test
    @Tag("D-353")
    @Tag("D-089")
    @Tag("D-140")
    void mostOverdueFirstAndDebitNoteBelowItsInvoice() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
        ReminderInvoiceFact marchDebitNote = ReminderInvoiceFacts.lateFeeDebitNote(2L, 1L, "2027-03-18", "2027-03-28");
        ReminderInvoiceFact april = ReminderInvoiceFacts.installment(3L, "2027-04-01", "2027-03-20");

        List<ReminderInvoiceFact> ordered = ReminderImputationOrder.sortMostOverdueFirst(List.of(april, marchDebitNote, march));

        assertThat(ordered).extracting(ReminderInvoiceFact::invoiceId).containsExactly(1L, 2L, 3L);
    }

    @Test
    @Tag("D-353")
    @Tag("D-140")
    void aDebitNoteStaysBelowItsInvoiceEvenWhenItsOwnDueDateIsEarlier() {
        ReminderInvoiceFact other = ReminderInvoiceFacts.installment(3L, "2027-03-01", "2027-03-18");
        ReminderInvoiceFact invoice = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-20");
        ReminderInvoiceFact debitNote = ReminderInvoiceFacts.lateFeeDebitNote(2L, 1L, "2027-03-05", "2027-03-15");

        List<ReminderInvoiceFact> ordered = ReminderImputationOrder.sortMostOverdueFirst(List.of(debitNote, invoice, other));

        assertThat(ordered).extracting(ReminderInvoiceFact::invoiceId).containsExactly(3L, 1L, 2L);
    }

    @Test
    @Tag("D-353")
    @Tag("D-140")
    void theOrderDoesNotDependOnTheInputOrder() {
        ReminderInvoiceFact march = ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11");
        ReminderInvoiceFact marchDebitNote = ReminderInvoiceFacts.lateFeeDebitNote(2L, 1L, "2027-03-18", "2027-03-28");
        ReminderInvoiceFact april = ReminderInvoiceFacts.installment(3L, "2027-04-01", "2027-04-11");
        ReminderInvoiceFact aprilDebitNote = ReminderInvoiceFacts.lateFeeDebitNote(4L, 3L, "2027-04-18", "2027-04-28");

        List<Long> expected = List.of(1L, 2L, 3L, 4L);

        assertThat(ReminderImputationOrder.sortMostOverdueFirst(List.of(aprilDebitNote, april, marchDebitNote, march))).extracting(ReminderInvoiceFact::invoiceId).isEqualTo(expected);
        assertThat(ReminderImputationOrder.sortMostOverdueFirst(List.of(marchDebitNote, aprilDebitNote, march, april))).extracting(ReminderInvoiceFact::invoiceId).isEqualTo(expected);
    }

    @Test
    @Tag("D-353")
    @Tag("D-140")
    void aDebitNoteWhoseInvoiceIsNotListedKeepsItsOwnPlaceByDueDate() {
        ReminderInvoiceFact orphanDebitNote = ReminderInvoiceFacts.lateFeeDebitNote(2L, 99L, "2027-03-18", "2027-03-28");
        ReminderInvoiceFact april = ReminderInvoiceFacts.installment(3L, "2027-04-01", "2027-04-11");
        ReminderInvoiceFact feb = ReminderInvoiceFacts.installment(4L, "2027-02-01", "2027-02-11");

        List<ReminderInvoiceFact> ordered = ReminderImputationOrder.sortMostOverdueFirst(List.of(april, orphanDebitNote, feb));

        assertThat(ordered).extracting(ReminderInvoiceFact::invoiceId).containsExactly(4L, 2L, 3L);
    }

    @Test
    @Tag("D-353")
    @Tag("D-097")
    void aFactExposesTheCurrencyOfItsBalance() {
        assertThat(ReminderInvoiceFacts.installment(1L, "2027-03-01", "2027-03-11").currency()).isEqualTo(com.red_escuela.backend.shared.domain.Currency.ARS);
    }

    @Test
    @Tag("D-353")
    @Tag("D-089")
    void invoicesWithTheSameDueDateKeepAStableOrderById() {
        ReminderInvoiceFact installment = ReminderInvoiceFacts.installment(5L, "2027-03-01", "2027-03-11");
        ReminderInvoiceFact enrollmentFee = ReminderInvoiceFacts.installment(4L, "2027-03-01", "2027-03-11");

        assertThat(ReminderImputationOrder.sortMostOverdueFirst(List.of(installment, enrollmentFee))).extracting(ReminderInvoiceFact::invoiceId).containsExactly(4L, 5L);
    }
}
