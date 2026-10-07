package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.domain.event.InvoiceDiscardedEvent;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoiceDocumentStatusTransitionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-051")
@Tag("D-009")
@Tag("D-021")
class DiscardDraftInvoiceTest {

    @Test
    void discardingADraftKeepsTheReasonReleasesTheChargeKeysAndRegistersTheEvent() {
        Invoice draft = withId(monthlyDraft("80000.00"), 6L);
        assertThat(draft.getLines().get(0).getChargeKeyReleasedAt()).isNull();

        draft.discardDraftInvoice("Error de carga", ACTOR);

        assertThat(draft.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DISCARDED);
        assertThat(draft.getDiscardedReason()).isEqualTo("Error de carga");
        assertThat(draft.getLines().get(0).getChargeKeyReleasedAt()).isNotNull();
        assertThat(draft.getStatusChanges().get(1).getReason()).isEqualTo("Error de carga");
        assertThat(draft.pullRecordedDomainEvents()).containsExactly(new InvoiceDiscardedEvent(6L, "Error de carga"));
    }

    @Test
    void anInvoiceAwaitingPaymentCanBeDiscardedToo() {
        Invoice annual = annualDraft(2);
        annual.submitDraftForPayment(LocalDateTime.of(2027, 3, 1, 12, 0), ACTOR);

        annual.discardDraftInvoice("Venció el plazo", ACTOR);

        assertThat(annual.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DISCARDED);
        assertThat(annual.getLines()).allSatisfy(line -> assertThat(line.getChargeKeyReleasedAt()).isNotNull());
    }

    @Test
    void anEmptyOrTooLongReasonIsRejectedAndTheDraftSurvives() {
        Invoice draft = monthlyDraft("80000.00");

        assertThatThrownBy(() -> draft.discardDraftInvoice(null, ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft.discardDraftInvoice(" ", ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft.discardDraftInvoice("x".repeat(501), ACTOR)).isInstanceOf(IllegalArgumentException.class);
        assertThat(draft.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DRAFT);
        assertThat(draft.getLines().get(0).getChargeKeyReleasedAt()).isNull();
        draft.discardDraftInvoice("x".repeat(500), ACTOR);
        assertThat(draft.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DISCARDED);
    }

    @Test
    void anIssuedInvoiceCannotBeDiscardedAndKeepsItsChargeKey() {
        Invoice issued = issuedMonthly("80000.00");

        assertThatThrownBy(() -> issued.discardDraftInvoice("x", ACTOR)).isInstanceOf(IllegalInvoiceDocumentStatusTransitionException.class);
        assertThat(issued.getLines().get(0).getChargeKeyReleasedAt()).isNull();
    }

    @Test
    void submittingForPaymentIsOnlyForAnnualUpfrontInvoicesWithLines() {
        Invoice monthly = monthlyDraft("80000.00");
        Invoice emptyAnnual = annualDraft(0);

        assertThatThrownBy(() -> monthly.submitDraftForPayment(LocalDateTime.of(2027, 3, 1, 0, 0), ACTOR)).isInstanceOf(IllegalInvoiceDocumentStatusTransitionException.class);
        assertThatThrownBy(() -> emptyAnnual.submitDraftForPayment(LocalDateTime.of(2027, 3, 1, 0, 0), ACTOR)).isInstanceOf(IllegalInvoiceDocumentStatusTransitionException.class);
        assertThatThrownBy(() -> annualDraft(1).submitDraftForPayment(null, ACTOR)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> annualDraft(1).submitDraftForPayment(LocalDateTime.of(2027, 3, 1, 0, 0), null)).isInstanceOf(NullPointerException.class);
        assertThat(monthly.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.DRAFT);
        assertThat(monthly.getPaymentDeadline()).isNull();
    }

    @Test
    void submittingRecordsTheDeadlineAndTheHistory() {
        Invoice annual = annualDraft(1);
        LocalDateTime deadline = LocalDateTime.of(2027, 3, 1, 12, 0);

        annual.submitDraftForPayment(deadline, ACTOR);

        assertThat(annual.getDocumentStatus()).isEqualTo(InvoiceDocumentStatus.AWAITING_PAYMENT);
        assertThat(annual.getPaymentDeadline()).isEqualTo(deadline);
        assertThat(annual.getStatusChanges().get(1).getToStatus()).isEqualTo("AWAITING_PAYMENT");
    }

    @Test
    void anAwaitingPaymentInvoiceAcceptsNoNewLines() {
        Invoice annual = annualDraft(1);
        annual.submitDraftForPayment(LocalDateTime.of(2027, 3, 1, 12, 0), ACTOR);

        assertThatThrownBy(() -> annual.addChargeLineToDraft(monthlyLine("10.00", "NEW"))).isInstanceOf(IllegalInvoiceDocumentStatusTransitionException.class);
        assertThat(annual.getLines()).hasSize(1);
    }
}
