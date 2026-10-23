package com.red_escuela.backend.billing.note.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-245")
@Tag("D-286")
class CancelInvoiceCommandTest {

    @Test
    void aBillingErrorCancellationByAPersonReleasesTheChargeKeys() {
        CancelInvoiceCommand command = CancelInvoiceCommand.byPerson(10L, CreditNoteReason.BILLING_ERROR, "Error", 3L, true);

        assertThat(command.releasesChargeKeys()).isTrue();
        assertThat(command.initiatedBySystem()).isFalse();
        assertThat(command.targetedInvoiceIds()).containsExactly(10L);
    }

    @Test
    void aCancellationByAPersonKeepsTheChargeKeys() {
        assertThat(CancelInvoiceCommand.byPerson(10L, CreditNoteReason.CANCELLATION, "Baja", 3L, false).releasesChargeKeys()).isFalse();
    }

    @Test
    void aSystemCancellationReleasesTheChargeKeysAndActsAsDirector() {
        CancelInvoiceCommand command = CancelInvoiceCommand.bySystem(10L, SystemCancellationOrigin.ADMISSION_EXPIRED, "Vencida");

        assertThat(command.releasesChargeKeys()).isTrue();
        assertThat(command.initiatedBySystem()).isTrue();
        assertThat(command.requestedByDirector()).isTrue();
        assertThat(command.reason()).isEqualTo(CreditNoteReason.CANCELLATION);
    }

    @Test
    void theAuthorizationReasonNamesTheReasonTheInvoiceAndTheJustification() {
        CancelInvoiceCommand command = CancelInvoiceCommand.byPerson(10L, CreditNoteReason.BILLING_ERROR, "Monto mal cargado", 3L, false);

        assertThat(command.authorizationReason()).isEqualTo("Cancelación (BILLING_ERROR) de la factura 10: Monto mal cargado");
        assertThat(command.authorizationExpiresOn()).isNull();
    }

    @Test
    void resumingFromAnAuthorizationRequestKeepsEveryOtherField() {
        CancelInvoiceCommand command = CancelInvoiceCommand.byPerson(10L, CreditNoteReason.BILLING_ERROR, "Error", 3L, false);

        CancelInvoiceCommand resumed = command.resumedFromAuthorizationRequest(77L);

        assertThat(resumed).isEqualTo(new CancelInvoiceCommand(10L, CreditNoteReason.BILLING_ERROR, "Error", 3L, false, null, 77L));
    }

    @Test
    void aCommandWithoutInvoiceIsRejected() {
        assertThatThrownBy(() -> CancelInvoiceCommand.byPerson(null, CreditNoteReason.BILLING_ERROR, "x", 3L, false)).isInstanceOf(NullPointerException.class);
    }
}
