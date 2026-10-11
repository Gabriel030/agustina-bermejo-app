package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.exception.CreditNoteDirectorApprovalRequiredException;
import com.red_escuela.backend.billing.note.usecase.CancelInvoiceBySystemUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.YearMonth;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-165")
@Tag("D-245")
class SystemCancellationIT extends AbstractInvoiceCancellationIT {

    @Autowired
    private CancelInvoiceBySystemUseCase systemCancellation;
    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private CancelInvoiceOperation operation;

    private Long issueWithKey(Long studentId, Long accountId, String chargeKey) {
        return inTransaction(() -> {
            Person person = studentPerson(studentId);
            StudentSnapshot snapshot = new StudentSnapshot(person.getName() + " " + person.getLastname(), person.getDni(), "Calle 1");
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                    InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Admision", YearMonth.of(2027, 3), null, null, null, null, null, chargeKey, ars("50000.00"));
            Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), DUE_DATE.minusDays(9), DUE_DATE, snapshot, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, DUE_DATE.minusDays(9));
            return saved.getId();
        });
    }

    @Test
    @Tag("B2-21")
    @Tag("D-229")
    void systemInitiatedCancellationNeedsNoRequestAndReleasesTheKey() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long invoiceId = issueWithKey(studentId, accountId, "ADM:" + System.nanoTime());
        long requestsBefore = jdbc.queryForObject("select count(*) from authorization_request", Long.class);

        systemCancellation.cancelInvoiceWithoutDirector(invoiceId, SystemCancellationOrigin.ADMISSION_EXPIRED, "La admision venció sin pago");

        assertThat(jdbc.queryForObject("select count(*) from authorization_request", Long.class)).isEqualTo(requestsBefore);
        assertThat(jdbc.queryForMap("select issued_by_process, issued_by_person_id, authorization_request_id, reason from credit_note where invoice_id = ?", invoiceId))
                .containsEntry("issued_by_process", "ADMISSION_EXPIRATION").containsEntry("issued_by_person_id", null)
                .containsEntry("authorization_request_id", null).containsEntry("reason", "CANCELLATION");
        assertThat(paymentStatusOf(invoiceId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ? and charge_key_released_at is not null", Long.class, invoiceId)).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-04")
    void aCommandThatClaimsSystemOriginWithAPersonOrWithoutOriginIsRejected() {
        Scenario scenario = unpaidInvoice("80000.00");
        Long person = newPersonId();

        assertThatThrownBy(() -> pipeline.runOperation(operation, new CancelInvoiceCommand(scenario.invoiceId(), com.red_escuela.backend.billing.note.domain.CreditNoteReason.CANCELLATION,
                "Baja", person, false, SystemCancellationOrigin.ADMISSION_EXPIRED, null))).isInstanceOf(CreditNoteDirectorApprovalRequiredException.class);
        assertThatThrownBy(() -> pipeline.runOperation(operation, new CancelInvoiceCommand(scenario.invoiceId(), com.red_escuela.backend.billing.note.domain.CreditNoteReason.CANCELLATION,
                "Baja", null, false, null, null))).isInstanceOf(CreditNoteDirectorApprovalRequiredException.class);
        assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
    }
}
