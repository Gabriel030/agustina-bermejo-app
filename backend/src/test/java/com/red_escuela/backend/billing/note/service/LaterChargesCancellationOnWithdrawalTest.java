package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.header;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.issue;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.withId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-204")
@Tag("D-300")
@Tag("D-247")
@Tag("B2-43")
class LaterChargesCancellationOnWithdrawalTest {
    private static final long TEMPLATE_2028 = 7L;

    private final WithdrawalInvoiceAccess invoiceAccess = mock(WithdrawalInvoiceAccess.class);
    private final CancelInvoiceOperation cancelInvoiceOperation = mock(CancelInvoiceOperation.class);
    private final BillingOperationPipeline pipeline = mock(BillingOperationPipeline.class);
    private final CancelInvoicesAfterWithdrawalOperation batchOperation = mock(CancelInvoicesAfterWithdrawalOperation.class);
    private final CreditNoteActorResolver actorResolver = mock(CreditNoteActorResolver.class);
    private final StudentCurrentAccountService accountService = mock(StudentCurrentAccountService.class);
    private final LaterChargesCancellationOnWithdrawal service = new LaterChargesCancellationOnWithdrawal(invoiceAccess, cancelInvoiceOperation, pipeline,
            batchOperation, actorResolver, accountService);

    @BeforeEach
    void stubTheEnrollmentInvoices() {
        Invoice june = withId(InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header(InvoiceType.MONTHLY_INSTALLMENT), new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT,
                "Cuota", YearMonth.of(2027, 6), 123L, null, null, null, 5L, "PE:123:M:2027-06", ars("80000.00")), Optional.empty(), ACTOR), 11L);
        issue(june);
        Invoice fee = withId(InvoiceFactory.createEnrollmentFeeInvoiceDraft(header(InvoiceType.ENROLLMENT_FEE), new InvoiceLineDraft(FeeType.ENROLLMENT_FEE, "Matrícula",
                YearMonth.of(2028, 2), 123L, null, null, null, 5L, "PE:123:F:2028", ars("50000.00"), TEMPLATE_2028, 1), Optional.empty(), ACTOR), 12L);
        issue(fee);
        when(invoiceAccess.findAccountIdsOfEnrollment(123L, null)).thenReturn(List.of(31L, 30L));
        when(invoiceAccess.findInvoicesOfEnrollment(123L, null)).thenReturn(List.of(june, fee));
        when(invoiceAccess.findInvoiceIdsWithPendingAuthorizationRequest(any())).thenReturn(java.util.Set.of());
        when(invoiceAccess.findTermStartOfTemplate(TEMPLATE_2028)).thenReturn(Optional.of(LocalDate.of(2028, 3, 1)));
        when(actorResolver.currentPersonId()).thenReturn(5L);
    }

    private WithdrawnEnrollmentFacts facts() {
        return WithdrawnEnrollmentFacts.ofProgramEnrollment(1L, 123L, LocalDate.of(2027, 5, 15), LocalDate.of(2027, 5, 15), WithdrawalCause.VOLUNTARY);
    }

    @Test
    void theAccountsAreLockedInIdOrderBeforeTheInvoicesAreReadAndPlanned() {
        service.cancelLaterChargesOf(facts());

        InOrder order = inOrder(invoiceAccess, accountService);
        order.verify(invoiceAccess).findAccountIdsOfEnrollment(123L, null);
        order.verify(accountService).lockStudentCurrentAccount(30L);
        order.verify(accountService).lockStudentCurrentAccount(31L);
        order.verify(invoiceAccess).findInvoicesOfEnrollment(123L, null);
    }

    @Test
    void theRequestIsOpenedBeforeTheAutomaticCancellationsSoTheirAllocationSkipsTheRequestedInvoices() {
        service.cancelLaterChargesOf(facts());

        InOrder order = inOrder(pipeline);
        order.verify(pipeline).runOperation(eq(batchOperation), any());
        order.verify(pipeline).runOperation(eq(cancelInvoiceOperation), any());
    }

    @Test
    void aWithdrawalForNonpaymentOrGraduationPlansNothing() {
        service.cancelLaterChargesOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(1L, 123L, LocalDate.of(2027, 5, 15), LocalDate.of(2027, 5, 15), WithdrawalCause.NONPAYMENT));

        verify(invoiceAccess, never()).findInvoicesOfEnrollment(any(), any());
        verify(pipeline, never()).runOperation(any(), any());
    }
}
