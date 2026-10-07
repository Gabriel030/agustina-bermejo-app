package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.IllegalInvoiceDocumentStatusTransitionException;
import com.red_escuela.backend.billing.invoice.exception.IssuedInvoiceCannotBeModifiedException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentEvent.*;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.AWAITING_PAYMENT;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DISCARDED;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DRAFT;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.ISSUED;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation.allows;
import static com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation.rejects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-036")
@Tag("D-051")
@ExhaustiveTransitionExpectations(machine = InvoiceDocumentStatusStateMachine.class)
class InvoiceDocumentStatusTransitionTest {

    private static final Class<IllegalInvoiceDocumentStatusTransitionException> ILLEGAL = IllegalInvoiceDocumentStatusTransitionException.class;

    static List<TransitionExpectation> expectedTransitions() {
        return List.of(
                allows(null, DRAFT_CREATED, DRAFT),
                rejects(null, DRAFT_CONTENT_CHANGED, ILLEGAL),
                rejects(null, DRAFT_SUBMITTED_FOR_PAYMENT, ILLEGAL),
                rejects(null, INVOICE_ISSUED, ILLEGAL),
                rejects(null, DRAFT_DISCARDED, ILLEGAL),
                rejects(DRAFT, DRAFT_CREATED, ILLEGAL),
                allows(DRAFT, DRAFT_CONTENT_CHANGED, DRAFT),
                allows(DRAFT, DRAFT_SUBMITTED_FOR_PAYMENT, AWAITING_PAYMENT),
                allows(DRAFT, INVOICE_ISSUED, ISSUED),
                allows(DRAFT, DRAFT_DISCARDED, DISCARDED),
                rejects(AWAITING_PAYMENT, DRAFT_CREATED, ILLEGAL),
                rejects(AWAITING_PAYMENT, DRAFT_CONTENT_CHANGED, ILLEGAL),
                rejects(AWAITING_PAYMENT, DRAFT_SUBMITTED_FOR_PAYMENT, ILLEGAL),
                allows(AWAITING_PAYMENT, INVOICE_ISSUED, ISSUED),
                allows(AWAITING_PAYMENT, DRAFT_DISCARDED, DISCARDED),
                rejects(ISSUED, DRAFT_CREATED, ILLEGAL),
                rejects(ISSUED, DRAFT_CONTENT_CHANGED, IssuedInvoiceCannotBeModifiedException.class),
                rejects(ISSUED, DRAFT_SUBMITTED_FOR_PAYMENT, ILLEGAL),
                rejects(ISSUED, INVOICE_ISSUED, ILLEGAL),
                rejects(ISSUED, DRAFT_DISCARDED, ILLEGAL),
                rejects(DISCARDED, DRAFT_CREATED, ILLEGAL),
                rejects(DISCARDED, DRAFT_CONTENT_CHANGED, ILLEGAL),
                rejects(DISCARDED, DRAFT_SUBMITTED_FOR_PAYMENT, ILLEGAL),
                rejects(DISCARDED, INVOICE_ISSUED, ILLEGAL),
                rejects(DISCARDED, DRAFT_DISCARDED, ILLEGAL));
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    private static Invoice invoiceIn(InvoiceDocumentStatus status) {
        Invoice invoice = annualDraft(1);
        switch (status) {
            case DRAFT -> {
            }
            case AWAITING_PAYMENT -> invoice.submitDraftForPayment(LocalDateTime.of(2027, 3, 1, 0, 0), ACTOR);
            case ISSUED -> issue(invoice);
            case DISCARDED -> invoice.discardDraftInvoice("motivo", ACTOR);
        }
        return invoice;
    }

    private static void apply(Invoice invoice, InvoiceDocumentEvent event) {
        switch (event) {
            case DRAFT_CREATED -> invoice.changeDocumentStatus(DRAFT_CREATED, ACTOR, null, null);
            case DRAFT_CONTENT_CHANGED -> invoice.addChargeLineToDraft(monthlyLine("500.00", "PE:123:MONTHLY_INSTALLMENT:2027-12"));
            case DRAFT_SUBMITTED_FOR_PAYMENT -> invoice.submitDraftForPayment(LocalDateTime.of(2027, 3, 1, 0, 0), ACTOR);
            case INVOICE_ISSUED -> issue(invoice);
            case DRAFT_DISCARDED -> invoice.discardDraftInvoice("otro motivo", ACTOR);
        }
    }

    private static long documentRows(Invoice invoice) {
        return invoice.getStatusChanges().stream().filter(c -> c.getStatusKind() == InvoiceStatusKind.DOCUMENT).count();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        InvoiceDocumentStatus from = (InvoiceDocumentStatus) expectation.fromState();
        InvoiceDocumentEvent event = (InvoiceDocumentEvent) expectation.event();
        if (from == null) {
            assertFromNoState(expectation, event);
            return;
        }
        Invoice invoice = invoiceIn(from);
        int lines = invoice.getLines().size();
        long rows = documentRows(invoice);
        var amounts = invoice.getAmounts();
        if (expectation.isAllowed()) {
            apply(invoice, event);
            assertThat(invoice.getDocumentStatus()).isEqualTo(expectation.toState());
            if (event == DRAFT_CONTENT_CHANGED) {
                assertThat(documentRows(invoice)).isEqualTo(rows);
            } else {
                assertThat(documentRows(invoice)).isEqualTo(rows + 1);
                InvoiceStatusChange last = invoice.getStatusChanges().stream()
                        .filter(c -> c.getStatusKind() == InvoiceStatusKind.DOCUMENT).reduce((a, b) -> b).orElseThrow();
                assertThat(last.getFromStatus()).isEqualTo(from.name());
                assertThat(last.getToStatus()).isEqualTo(expectation.toState().name());
                assertThat(last.getEvent()).isEqualTo(event.name());
                assertThat(last.getActorProcess()).isEqualTo("TEST");
            }
        } else {
            assertThatThrownBy(() -> apply(invoice, event)).isInstanceOf(expectation.rejection());
            assertThat(invoice.getDocumentStatus()).isEqualTo(from);
            assertThat(documentRows(invoice)).isEqualTo(rows);
            assertThat(invoice.getLines()).hasSize(lines);
            assertThat(invoice.getAmounts()).isEqualTo(amounts);
        }
    }

    private static void assertFromNoState(TransitionExpectation expectation, InvoiceDocumentEvent event) {
        if (expectation.isAllowed()) {
            Invoice created = annualDraft(0);
            assertThat(created.getDocumentStatus()).isEqualTo(DRAFT);
            assertThat(created.getStatusChanges()).singleElement().satisfies(change -> {
                assertThat(change.getFromStatus()).isNull();
                assertThat(change.getToStatus()).isEqualTo("DRAFT");
                assertThat(change.getEvent()).isEqualTo("DRAFT_CREATED");
            });
        } else {
            assertThatThrownBy(() -> InvoiceDocumentStatusStateMachine.resolveNextStatus(null, event)).isInstanceOf(expectation.rejection());
        }
    }
}
