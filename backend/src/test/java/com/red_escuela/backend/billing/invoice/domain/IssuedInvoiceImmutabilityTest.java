package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.IssuedInvoiceCannotBeModifiedException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-036")
@Tag("D-091")
class IssuedInvoiceImmutabilityTest {

    private static final Set<String> ALLOWED_DRAFT_AND_PAYMENT_OPERATIONS = Set.of(
            "addChargeLineToDraft", "addDiscountToDraftLine", "submitDraftForPayment", "issueInvoice", "discardDraftInvoice",
            "markPaymentUnderReview", "releasePaymentReview", "allocateFullInvoicePayment", "reversePaymentAllocation",
            "applyCreditNote", "markInvoiceAsOverdue", "pullRecordedDomainEvents");

    @Test
    void anIssuedInvoiceRejectsLinesAndDiscountsAndNothingChanges() {
        Invoice issued = annualDraft(1);
        issue(issued);
        var amounts = issued.getAmounts();
        int rows = issued.getStatusChanges().size();

        assertThatThrownBy(() -> issued.addChargeLineToDraft(monthlyLine("10.00", "NEW"))).isInstanceOf(IssuedInvoiceCannotBeModifiedException.class);
        assertThatThrownBy(() -> issued.addDiscountToDraftLine(1, discount("1000.00", "10.00"))).isInstanceOf(IssuedInvoiceCannotBeModifiedException.class);

        assertThat(issued.getLines()).hasSize(1);
        assertThat(issued.getAmounts()).isEqualTo(amounts);
        assertThat(issued.getStatusChanges()).hasSize(rows);
    }

    @Test
    void theAggregateExposesNoPublicMethodThatChangesNumberDatesLinesDiscountsOrSnapshot() {
        List<String> mutators = Arrays.stream(Invoice.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> !name.startsWith("get") && !name.startsWith("is"))
                .toList();

        assertThat(mutators).allMatch(ALLOWED_DRAFT_AND_PAYMENT_OPERATIONS::contains);
        assertThat(mutators).noneMatch(name -> name.startsWith("set"));
    }

    @Test
    void linesAndDiscountsExposeNoPublicMutator() {
        for (Class<?> type : List.of(InvoiceLine.class, InvoiceDiscount.class, InvoiceStatusChange.class)) {
            assertThat(Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .map(Method::getName))
                    .allMatch(name -> name.startsWith("get") || name.startsWith("calculate") || name.startsWith("describe"));
        }
    }

    @Test
    void theExposedCollectionsCannotBeModifiedFromOutside() {
        Invoice invoice = monthlyDraft("10.00");

        assertThatThrownBy(() -> invoice.getLines().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> invoice.getStatusChanges().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aPaidInvoiceCannotBeCancelledByACancellationCreditNote() {
        Invoice invoice = issuedMonthly("80000.00");
        invoice.allocateFullInvoicePayment(ars("80000.00"), DUE_DATE, ACTOR);

        assertThatThrownBy(() -> invoice.applyCreditNote(creditNote(CreditNoteApplicationKind.CANCELLATION, "80000.00", "0.00"), DUE_DATE, ACTOR))
                .isInstanceOf(com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException.class);
        assertThat(invoice.getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }
}
