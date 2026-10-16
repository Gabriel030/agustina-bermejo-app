package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import com.red_escuela.backend.billing.account.service.AllocationSourceQueryService;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShare;
import com.red_escuela.backend.billing.invoice.service.InvoicePaymentShares;
import com.red_escuela.backend.billing.payment.domain.CardPaymentDetails;
import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentFixtures;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.identity.api.PersonNameLookup;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.payment.domain.PaymentFixtures.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllocationSourceInvoicePaymentShareSourceTest {

    private static final long ACCOUNT_ID = 55L;

    private final AllocationSourceQueryService sources = mock(AllocationSourceQueryService.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PersonNameLookup names = mock(PersonNameLookup.class);
    private final AllocationSourceInvoicePaymentShareSource shareSource = new AllocationSourceInvoicePaymentShareSource(sources, payments, names);

    private static Payment withId(Payment payment, long id) {
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "studentCurrentAccountId", ACCOUNT_ID);
        return payment;
    }

    private static PaymentAllocationSource source(long paymentId, String amount) {
        PaymentAllocationSource source = mock(PaymentAllocationSource.class);
        when(source.getPaymentId()).thenReturn(paymentId);
        when(source.getStudentCurrentAccountId()).thenReturn(ACCOUNT_ID);
        when(source.getAmount()).thenReturn(ars(amount));
        return source;
    }

    private static PaymentAllocationSource creditNoteSource(String amount) {
        PaymentAllocationSource source = mock(PaymentAllocationSource.class);
        when(source.getPaymentId()).thenReturn(null);
        when(source.getAmount()).thenReturn(ars(amount));
        return source;
    }

    private static Payment mercadoPagoCard() {
        Payment payment = Payment.registerApprovedGatewayPayment(PaymentFixtures.arsAccount(), ars("80000.00"), PaymentGatewayCode.MERCADO_PAGO,
                new CardPaymentDetails(CardPaymentType.CREDIT, 3, "4242"), "mp-77", new GatewayRawStatus("approved", "accredited"),
                LocalDate.of(2027, 3, 20), StatusChangeActor.ofProcess("MP_WEBHOOK"));
        return withId(payment, 31L);
    }

    private void givenSources(long invoiceId, PaymentAllocationSource... invoiceSources) {
        when(sources.findCurrentSourcesByInvoiceId(any())).thenReturn(Map.of(invoiceId, List.of(invoiceSources)));
    }

    private void givenPayments(Payment... found) {
        when(payments.findByIdIn(any())).thenAnswer(call -> {
            Collection<Long> ids = call.getArgument(0);
            return List.of(found).stream().filter(payment -> ids.contains(payment.getId())).toList();
        });
        when(names.findFullNamesByPersonId(any())).thenReturn(Map.of(7L, "Ana Ruiz"));
    }

    @Test
    @Tag("D-376")
    void twoSourcesOfTheSamePaymentOnAnInvoiceAreSummedIntoOneShare() {
        Payment cash = withId(PaymentFixtures.cashPayment(), 30L);
        givenSources(1L, source(30L, "30000.00"), source(30L, "20000.00"));
        givenPayments(cash);

        List<InvoicePaymentShare> shares = shareSource.findCurrentSharesByInvoiceId(List.of(1L)).resolvedByInvoiceId().get(1L);

        assertThat(shares).hasSize(1);
        assertThat(shares.get(0).amountApplied()).isEqualTo(ars("50000.00").value());
        assertThat(shares.get(0).paymentTotal()).isEqualTo(ars("80000.00").value());
        assertThat(shares.get(0).method()).isEqualTo("CASH");
        assertThat(shares.get(0).externalReference()).isEqualTo("R-1");
        assertThat(shares.get(0).registeredByName()).isEqualTo("Ana Ruiz");
        assertThat(shares.get(0).isConfirmed()).isTrue();
    }

    @Test
    @Tag("D-376")
    void aMercadoPagoCardPaymentKeepsTheLegacyLabelsAndCardData() {
        Payment card = mercadoPagoCard();
        givenSources(1L, source(31L, "80000.00"));
        givenPayments(card);

        InvoicePaymentShare share = shareSource.findCurrentSharesByInvoiceId(List.of(1L)).resolvedByInvoiceId().get(1L).get(0);

        assertThat(share.method()).isEqualTo("MERCADO_PAGO");
        assertThat(share.cardType()).isEqualTo("CREDIT");
        assertThat(share.cardInstallments()).isEqualTo(3);
        assertThat(share.externalReference()).isEqualTo("mp-77");
        assertThat(share.registeredByName()).isEqualTo("MP_WEBHOOK");
        assertThat(share.paidAt()).isEqualTo(LocalDate.of(2027, 3, 20).atStartOfDay());
    }

    @Test
    @Tag("D-376")
    void aBankTransferIsLabelledTransferAndAPaymentFromAnotherAccountIsIgnored() {
        Payment transfer = withId(PaymentFixtures.pendingBankTransfer(), 32L);
        transfer.approvePayment(ars("80000.00"), LocalDate.of(2027, 3, 15), StatusChangeActor.ofPerson(7L), "ok");
        PaymentAllocationSource foreign = source(32L, "10000.00");
        when(foreign.getStudentCurrentAccountId()).thenReturn(999L);
        givenSources(1L, source(32L, "5000.00"), foreign);
        givenPayments(transfer);

        List<InvoicePaymentShare> shares = shareSource.findCurrentSharesByInvoiceId(List.of(1L)).resolvedByInvoiceId().get(1L);

        assertThat(shares).singleElement().satisfies(share -> {
            assertThat(share.method()).isEqualTo("TRANSFER");
            assertThat(share.externalReference()).isEqualTo("OP-9");
            assertThat(share.notes()).isEqualTo("ok");
            assertThat(share.amountApplied()).isEqualTo(ars("5000.00").value());
        });
    }

    @Test
    @Tag("D-376")
    void creditNoteSourcesAndUnknownOrUnapprovedPaymentsAreNotListedAsPayments() {
        Payment pending = withId(PaymentFixtures.pendingBankTransfer(), 33L);
        givenSources(1L, creditNoteSource("1000.00"), source(33L, "2000.00"), source(404L, "3000.00"));
        givenPayments(pending);

        assertThat(shareSource.findCurrentSharesByInvoiceId(List.of(1L)).resolvedByInvoiceId()).isEmpty();
    }

    @Test
    @Tag("D-376")
    void aSourceWhoseIdIsNotAnAccountPaymentIsHandedBackAsLegacyAndNotDropped() {
        Payment cash = withId(PaymentFixtures.cashPayment(), 30L);
        givenSources(1L, source(30L, "2000.00"), source(404L, "3000.00"), source(404L, "500.00"));
        givenPayments(cash);

        InvoicePaymentShares shares = shareSource.findCurrentSharesByInvoiceId(List.of(1L));

        assertThat(shares.resolvedByInvoiceId().get(1L)).extracting(InvoicePaymentShare::paymentId).containsExactly(30L);
        assertThat(shares.unresolvedAppliedByPaymentIdByInvoiceId().get(1L)).containsOnlyKeys(404L);
        assertThat(shares.unresolvedAppliedByPaymentIdByInvoiceId().get(1L).get(404L)).isEqualTo(ars("3500.00").value());
    }
}
