package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.configuration.domain.ConfigurationScope;
import com.red_escuela.backend.billing.configuration.domain.ConfigurationTopic;
import com.red_escuela.backend.billing.configuration.policy.ChangeBillingConfigurationCommand;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationChangeAuthorizationRequestDescriber;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionType;
import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.operation.CloseDiscountDefinitionCommand;
import com.red_escuela.backend.billing.discount.operation.CreateDiscountDefinitionCommand;
import com.red_escuela.backend.billing.discount.operation.GrantStudentDiscountCommand;
import com.red_escuela.backend.billing.discount.operation.RevokeStudentDiscountCommand;
import com.red_escuela.backend.billing.discount.service.CloseDiscountDefinitionAuthorizationRequestDescriber;
import com.red_escuela.backend.billing.discount.service.CreateDiscountDefinitionAuthorizationRequestDescriber;
import com.red_escuela.backend.billing.discount.service.GrantStudentDiscountAuthorizationRequestDescriber;
import com.red_escuela.backend.billing.discount.service.RevokeStudentDiscountAuthorizationRequestDescriber;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CancelInvoicesAfterWithdrawalCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationItem;
import com.red_escuela.backend.billing.note.domain.WithdrawalLineAmount;
import com.red_escuela.backend.billing.note.service.CancelInvoiceAuthorizationRequestDescriber;
import com.red_escuela.backend.billing.note.service.CreditNoteAuthorizationRequestDescriber;
import com.red_escuela.backend.billing.note.service.InvoiceBriefReader;
import com.red_escuela.backend.billing.note.service.InvoiceBriefReader.InvoiceBrief;
import com.red_escuela.backend.billing.note.service.WithdrawalCancellationAuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlockType;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.billing.payment.domain.RefundPart;
import com.red_escuela.backend.billing.payment.operation.IssueRefundCommand;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.billing.payment.service.RefundAuthorizationRequestDescriber;
import com.red_escuela.backend.identity.api.StudentNameLookup;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-216")
class AuthorizationRequestDescribersTest {

    private static final Money TOTAL = Money.of(new BigDecimal("520000.00"), Currency.ARS);

    private final InvoiceBriefReader invoiceBriefs = mock(InvoiceBriefReader.class);

    private static Map<String, String> valuesOf(AuthorizationRequestDescription description) {
        return description.requestLines().stream().collect(Collectors.toMap(DescriptionLine::label, DescriptionLine::value));
    }

    private void invoiceExists(long id, String number, String concept, String student) {
        when(invoiceBriefs.findBriefOf(id)).thenReturn(Optional.of(new InvoiceBrief(id, number, concept, student, TOTAL)));
    }

    @Test
    void aCancellationNamesTheStudentTheInvoiceTheAmountAndTheReason() {
        invoiceExists(7L, "F-00002201", "Cuota octubre 2026", "Benítez, Joaquín");

        AuthorizationRequestDescription description = new CancelInvoiceAuthorizationRequestDescriber(invoiceBriefs)
                .describe(CancelInvoiceCommand.byPerson(7L, CreditNoteReason.BILLING_ERROR, "Se emitió por error", 3L, false), 11L);

        assertThat(description.summary()).isEqualTo("Cancelar la factura F-00002201 · Cuota octubre 2026");
        assertThat(description.studentName()).isEqualTo("Benítez, Joaquín");
        assertThat(description.amount()).isEqualTo(TOTAL);
        assertThat(valuesOf(description)).containsEntry("Alumno", "Benítez, Joaquín").containsEntry("Factura", "F-00002201 · Cuota octubre 2026")
                .containsEntry("Importe", "$520.000").containsEntry("Motivo", "Error de facturación");
        assertThat(description.effectLines()).hasSize(1);
        assertThat(description.effectLines().get(0).tone()).isEqualTo(EffectTone.SUCCESS);
        assertThat(description.detailBlock().type()).isEqualTo(DetailBlockType.NONE);
    }

    @Test
    void aCancellationOfAnInvoiceThatCannotBeFoundStillDescribesTheReasonWithoutInventingTheRest() {
        when(invoiceBriefs.findBriefOf(8L)).thenReturn(Optional.empty());

        AuthorizationRequestDescription description = new CancelInvoiceAuthorizationRequestDescriber(invoiceBriefs)
                .describe(CancelInvoiceCommand.byPerson(8L, CreditNoteReason.CANCELLATION, "Duplicada", 3L, false), 11L);

        assertThat(description.summary()).isEqualTo("Cancelar la factura 8");
        assertThat(description.studentName()).isNull();
        assertThat(description.amount()).isNull();
        assertThat(valuesOf(description)).containsOnlyKeys("Motivo").containsEntry("Motivo", "Cancelación");
        assertThat(description.effectLines()).isEmpty();
    }

    @Test
    void aCreditNoteAddsUpItsLinesAndSaysWhereTheCreditGoes() {
        invoiceExists(9L, "F-00002104", null, "Suárez, Valentina");
        List<CreditNoteLineAmount> lines = List.of(new CreditNoteLineAmount(1, Money.of(new BigDecimal("100000.00"), Currency.ARS)),
                new CreditNoteLineAmount(2, Money.of(new BigDecimal("50000.50"), Currency.ARS)));

        AuthorizationRequestDescription description = new CreditNoteAuthorizationRequestDescriber(invoiceBriefs).describe(
                new IssueCreditNoteCommand(9L, CreditNoteReason.BILLING_ERROR, "Beca", lines, CreditNoteDestination.FREE_CREDIT, 3L, false, null), 12L);

        assertThat(description.amount()).isEqualTo(Money.of(new BigDecimal("150000.50"), Currency.ARS));
        assertThat(description.summary()).isEqualTo("Nota de crédito sobre F-00002104");
        assertThat(valuesOf(description)).containsEntry("Importe", "$150.000,50").containsEntry("Destino", "Saldo a favor del alumno")
                .containsEntry("Factura", "F-00002104").containsEntry("Alumno", "Suárez, Valentina");
        assertThat(new CreditNoteAuthorizationRequestDescriber(invoiceBriefs).describe(
                new IssueCreditNoteCommand(9L, CreditNoteReason.BILLING_ERROR, "Beca", List.of(), CreditNoteDestination.DEBT_OFFSET, 3L, false, null), 12L).amount()).isNull();
    }

    @Test
    void aWithdrawalCancellationCountsWhatIsCancelledAndWhatIsReducedAndTakesTheStudentFromTheFirstInvoice() {
        invoiceExists(21L, "F-1", "Cuota", "Gómez, Martín");
        List<WithdrawalCancellationItem> items = List.of(WithdrawalCancellationItem.cancelling(21L), WithdrawalCancellationItem.cancelling(22L),
                WithdrawalCancellationItem.reducing(23L, List.of(new WithdrawalLineAmount(1, Money.of(new BigDecimal("10.00"), Currency.ARS)))));

        AuthorizationRequestDescription description = new WithdrawalCancellationAuthorizationRequestDescriber(invoiceBriefs)
                .describe(new CancelInvoicesAfterWithdrawalCommand(items, LocalDate.of(2026, 10, 5), 3L, false, null), 13L);

        assertThat(description.summary()).isEqualTo("Baja del 05/10/2026: 3 facturas");
        assertThat(description.studentName()).isEqualTo("Gómez, Martín");
        assertThat(valuesOf(description)).containsEntry("Baja", "Fecha de baja 05/10/2026").containsEntry("Facturas", "2 a cancelar, 1 a reducir");
    }

    @Test
    void aGrantedDiscountShowsTheBenefitAndThePeriodsInPlainSpanish() {
        StudentDiscountGrantRequest request = new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, null, new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("50"))),
                InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2026, 11), YearMonth.of(2027, 3), "Beca");

        AuthorizationRequestDescription description = new GrantStudentDiscountAuthorizationRequestDescriber()
                .describe(GrantStudentDiscountCommand.byPerson(new EnrollmentReference(EnrollmentKind.PROGRAM, 44L), request, 3L, false), 14L);

        assertThat(description.summary()).isEqualTo("Alta de descuento de 50 %");
        assertThat(valuesOf(description)).containsEntry("Inscripción", "N.º 44").containsEntry("Beneficio", "50 %").containsEntry("Desde", "Cuota 11/2026")
                .containsEntry("Hasta", "Cuota 03/2027");
        StudentDiscountGrantRequest nominal = new StudentDiscountGrantRequest(DiscountType.INDIVIDUAL, null, new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("2500.00"), Currency.ARS)),
                null, null, null, null);
        AuthorizationRequestDescription nominalDescription = new GrantStudentDiscountAuthorizationRequestDescriber()
                .describe(GrantStudentDiscountCommand.byPerson(new EnrollmentReference(EnrollmentKind.COURSE, 5L), nominal, 3L, false), 14L);
        assertThat(nominalDescription.summary()).isEqualTo("Alta de descuento de $2.500");
        assertThat(valuesOf(nominalDescription)).doesNotContainKeys("Desde", "Hasta");
    }

    @Test
    void aRevocationAndAClosureCarryTheirReasonAndAWarningTone() {
        AuthorizationRequestDescription revocation = new RevokeStudentDiscountAuthorizationRequestDescriber()
                .describe(new RevokeStudentDiscountCommand(31L, "  Dejó de cursar  ", 3L, false, LocalDate.of(2026, 10, 1)), 15L);
        AuthorizationRequestDescription closure = new CloseDiscountDefinitionAuthorizationRequestDescriber()
                .describe(new CloseDiscountDefinitionCommand(32L, " Fin de campaña ", 3L, false), 16L);

        assertThat(revocation.summary()).isEqualTo("Revocar el descuento 31");
        assertThat(valuesOf(revocation)).containsEntry("Motivo de la revocación", "Dejó de cursar");
        assertThat(revocation.effectLines().get(0).tone()).isEqualTo(EffectTone.WARNING);
        assertThat(closure.summary()).isEqualTo("Cerrar la promoción 32");
        assertThat(valuesOf(closure)).containsEntry("Motivo del cierre", "Fin de campaña");
    }

    @Test
    void aNewPromotionShowsItsNameBenefitAndAvailability() {
        DiscountDefinitionDraft draft = new DiscountDefinitionDraft("  Pago anual  ", DiscountDefinitionType.ANNUAL_UPFRONT, InvoiceType.ANNUAL_UPFRONT,
                new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("15.5"))), null, LocalDate.of(2026, 11, 1), null, 1, null, null, null, null, null, null, null);

        AuthorizationRequestDescription description = new CreateDiscountDefinitionAuthorizationRequestDescriber()
                .describe(CreateDiscountDefinitionCommand.byPerson(draft, 3L, false), 17L);

        assertThat(description.summary()).isEqualTo("Alta de la promoción Pago anual");
        assertThat(valuesOf(description)).containsEntry("Promoción", "Pago anual").containsEntry("Beneficio", "15,5 %").containsEntry("Disponible desde", "01/11/2026")
                .doesNotContainKey("Disponible hasta");
    }

    @Test
    void aConfigurationChangeNamesTheTopicTheDateAndTheReason() {
        ChangeBillingConfigurationCommand command = new ChangeBillingConfigurationCommand(new ConfigurationTopic(ConfigurationScope.LATE_PAYMENT, null, null, null, null), null,
                LocalDate.of(2026, 12, 1), " Nuevo recargo ", 3L, false);

        AuthorizationRequestDescription description = new BillingConfigurationChangeAuthorizationRequestDescriber().describe(command, 18L);

        assertThat(description.summary()).isEqualTo("Cambio de recargo por mora");
        assertThat(valuesOf(description)).containsEntry("Configuración", "Recargo por mora").containsEntry("Vigente desde", "01/12/2026")
                .containsEntry("Motivo del cambio", "Nuevo recargo");
        assertThat(description.effectLines().get(0).text()).isEqualTo("La nueva configuración rige desde el 01/12/2026.");
    }

    @Test
    void aRefundListsItsPartsPluralizesTheEffectAndFallsBackToTheOriginalPaymentDestination() {
        RefundRepository refunds = mock(RefundRepository.class);
        StudentNameLookup names = mock(StudentNameLookup.class);
        when(names.findFullNamesByStudentId(List.of(5L))).thenReturn(Map.of(5L, "Ledesma, Florencia"));
        LocalDate today = LocalDate.of(2026, 10, 22);
        when(refunds.findByAuthorizationRequestIdOrderByIdAsc(19L)).thenReturn(List.of(
                Refund.requestPartOf(1L, 2L, new RefundPart(100L, PaymentMethodType.CASH, NonNegativeMoney.of(new BigDecimal("70000.00"), Currency.ARS), new RefundDestination("alias.pedido", "Titular")), 3L, 19L, today),
                Refund.requestPartOf(1L, 2L, new RefundPart(101L, PaymentMethodType.BANK_TRANSFER, NonNegativeMoney.of(new BigDecimal("50000.00"), Currency.ARS),
                        new RefundDestination("0170099220000067890123", "Florencia Ledesma")), 3L, 19L, today)));
        RefundAuthorizationRequestDescriber describer = new RefundAuthorizationRequestDescriber(refunds, names);

        AuthorizationRequestDescription description = describer.describe(new IssueRefundCommand(5L, Money.of(new BigDecimal("120000.00"), Currency.ARS), null, null, 3L, false, 19L), 19L);

        assertThat(description.summary()).isEqualTo("Devolver saldo a favor, 2 pagos");
        assertThat(description.studentName()).isEqualTo("Ledesma, Florencia");
        assertThat(valuesOf(description)).containsEntry("Pedido", "Reembolso de $120.000").containsEntry("Destino", "El de cada pago original");
        assertThat(description.effectLines().get(0).text()).isEqualTo("Las 2 partes pasan a Aprobado y quedan listas para transferir.");
        assertThat(description.effectLines().get(2).text()).isEqualTo("Si se rechaza, $120.000 vuelve a estar disponible para el alumno.");
        assertThat(description.detailBlock().type()).isEqualTo(DetailBlockType.REFUND_PARTS);
        assertThat(description.detailBlock().refundParts()).extracting(part -> part.paymentId(), part -> part.methodType(), part -> part.status(), part -> part.destinationHolder())
                .containsExactly(tuple(100L, "CASH", "REQUESTED", "Titular"), tuple(101L, "BANK_TRANSFER", "REQUESTED", "Florencia Ledesma"));
        assertThat(describer.describe(new IssueRefundCommand(5L, Money.of(new BigDecimal("120000.00"), Currency.ARS), "alias.pedido", "Titular", 3L, false, 19L), 19L)
                .requestLines().get(2).value()).isEqualTo("alias.pedido · Titular");
    }
}
