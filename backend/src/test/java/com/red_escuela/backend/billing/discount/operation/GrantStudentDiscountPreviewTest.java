package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.DiscountPolicySetsConfiguration;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantPreview;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.repository.DiscountDefinitionRepository;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.discount.service.AnnualUpfrontCoverageReader;
import com.red_escuela.backend.billing.discount.service.EnrollmentDiscountLock;
import com.red_escuela.backend.billing.discount.service.EnrollmentFeeIssuanceReader;
import com.red_escuela.backend.billing.discount.service.EnrollmentLectiveYearReader;
import com.red_escuela.backend.billing.discount.service.NextMonthlyInstallmentPeriodQuery;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GrantStudentDiscountPreviewTest {
    private static final EnrollmentReference ENROLLMENT = EnrollmentReference.ofProgram(5L);
    private static final YearMonth NEXT_INSTALLMENT = YearMonth.of(2027, 4);
    private static final MonetaryAdjustment THIRTY_PERCENT = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("30")));

    private final BillingEnrollmentLookup enrollmentLookup = mock(BillingEnrollmentLookup.class);
    private final DiscountDefinitionRepository definitionRepository = mock(DiscountDefinitionRepository.class);
    private final StudentDiscountRepository repository = mock(StudentDiscountRepository.class);
    private final NextMonthlyInstallmentPeriodQuery nextInstallmentQuery = mock(NextMonthlyInstallmentPeriodQuery.class);
    private final AnnualUpfrontCoverageReader annualUpfrontReader = mock(AnnualUpfrontCoverageReader.class);
    private final EnrollmentLectiveYearReader lectiveYearReader = mock(EnrollmentLectiveYearReader.class);
    private final EnrollmentFeeIssuanceReader enrollmentFeeReader = mock(EnrollmentFeeIssuanceReader.class);
    private final EnrollmentDiscountLock enrollmentLock = mock(EnrollmentDiscountLock.class);
    private GrantStudentDiscountOperation operation;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2027-03-10T12:00:00Z"), ZoneOffset.UTC);
        when(enrollmentLookup.findProgramEnrollmentFacts(5L)).thenReturn(Optional.of(new BillingEnrollmentFacts(9L, Currency.ARS)));
        when(nextInstallmentQuery.findNextMonthlyInstallmentPeriod(any(), any())).thenReturn(NEXT_INSTALLMENT);
        when(lectiveYearReader.findLectiveYearCovering(any(), any(), any())).thenReturn(Optional.empty());
        operation = new GrantStudentDiscountOperation(new DiscountPolicySetsConfiguration().studentDiscountGrantingPolicySet(), enrollmentLookup, definitionRepository, repository,
                nextInstallmentQuery, annualUpfrontReader, lectiveYearReader, enrollmentFeeReader, enrollmentLock, clock);
    }

    private StudentDiscountGrantRequest individual(InvoiceType invoiceType, YearMonth from, YearMonth to) {
        return new StudentDiscountGrantRequest(DiscountType.INDIVIDUAL, null, THIRTY_PERCENT, invoiceType, from, to, "Hermano en la institución");
    }

    private StudentDiscountGrantPreview preview(StudentDiscountGrantRequest request, boolean byDirector) {
        return operation.previewGrant(GrantStudentDiscountCommand.byPerson(ENROLLMENT, request, 1L, byDirector));
    }

    @Test
    @Tag("D-405")
    void anIndividualDiscountOnTheEnrollmentFeeIsBlockedWhenTheLectiveYearFeeWasAlreadyIssued() {
        when(enrollmentFeeReader.enrollmentFeeIssuedIn(any(), any())).thenReturn(true);

        StudentDiscountGrantPreview preview = preview(individual(InvoiceType.ENROLLMENT_FEE, YearMonth.of(2027, 4), null), true);

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.enrollmentFeeAlreadyIssued()).isTrue();
        assertThat(preview.violations()).extracting(violation -> violation.code()).containsExactly("ENROLLMENT_FEE_ALREADY_ISSUED");
        assertThat(preview.toPeriod()).isEqualTo(YearMonth.of(2027, 4));
    }

    @Test
    @Tag("D-405")
    void anIndividualDiscountOnTheEnrollmentFeeIsAllowedWhenTheFeeWasNotIssued() {
        when(enrollmentFeeReader.enrollmentFeeIssuedIn(any(), any())).thenReturn(false);

        StudentDiscountGrantPreview preview = preview(individual(InvoiceType.ENROLLMENT_FEE, YearMonth.of(2027, 4), null), true);

        assertThat(preview.allowed()).isTrue();
        assertThat(preview.enrollmentFeeAlreadyIssued()).isFalse();
        assertThat(preview.coveredPeriods()).isEqualTo(1);
    }

    @Test
    @Tag("D-282")
    void aScholarshipOverAnAnnualUpfrontPaymentIsBlocked() {
        when(annualUpfrontReader.annualUpfrontCoversAnyMonthOf(any(), any(), any())).thenReturn(true);
        when(definitionRepository.findFirstByDiscountTypeAndStatusOrderByIdAsc(any(), any())).thenReturn(Optional.empty());
        StudentDiscountGrantRequest scholarship = new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, null, THIRTY_PERCENT, InvoiceType.MONTHLY_INSTALLMENT,
                YearMonth.of(2027, 4), YearMonth.of(2027, 8), "Situación económica");

        StudentDiscountGrantPreview preview = preview(scholarship, true);

        assertThat(preview.annualUpfrontCoversRange()).isTrue();
        assertThat(preview.violations()).extracting(violation -> violation.code()).contains("SCHOLARSHIP_NOT_AVAILABLE_WITH_ANNUAL_UPFRONT");
    }

    @Test
    @Tag("D-282")
    void aScholarshipStartingBeforeTheNextInstallmentIsBlockedAndTheNextInstallmentIsReported() {
        when(definitionRepository.findFirstByDiscountTypeAndStatusOrderByIdAsc(any(), any())).thenReturn(Optional.empty());
        StudentDiscountGrantRequest scholarship = new StudentDiscountGrantRequest(DiscountType.SCHOLARSHIP, null, THIRTY_PERCENT, InvoiceType.MONTHLY_INSTALLMENT,
                YearMonth.of(2027, 3), YearMonth.of(2027, 8), "Situación económica");

        StudentDiscountGrantPreview preview = preview(scholarship, true);

        assertThat(preview.nextInstallmentPeriod()).isEqualTo(NEXT_INSTALLMENT);
        assertThat(preview.violations()).extracting(violation -> violation.code()).contains("SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT");
    }

    @Test
    void theCoveredPeriodsAreCountedAcrossTheYearBoundary() {
        StudentDiscountGrantPreview preview = preview(individual(InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 11), YearMonth.of(2028, 2)), true);

        assertThat(preview.allowed()).isTrue();
        assertThat(preview.coveredPeriods()).isEqualTo(4);
    }

    @Test
    void aRangeEndingBeforeItStartsCoversNoPeriodAndIsBlocked() {
        StudentDiscountGrantPreview preview = preview(individual(InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 8), YearMonth.of(2027, 5)), true);

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.coveredPeriods()).isZero();
        assertThat(preview.violations()).extracting(violation -> violation.code()).contains("INVALID_SCHOLARSHIP_PERIOD");
    }

    @Test
    void theApprovalRequirementFollowsTheActor() {
        StudentDiscountGrantRequest request = individual(InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 5), YearMonth.of(2027, 6));

        assertThat(preview(request, true).requiresDirectorApproval()).isFalse();
        assertThat(preview(request, false).requiresDirectorApproval()).isTrue();
    }

    @Test
    void thePreviewNeverLocksNorWritesAnything() {
        preview(individual(InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 5), YearMonth.of(2027, 6)), true);

        verifyNoInteractions(enrollmentLock);
        verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void anOverlappingActiveDiscountIsReportedWithItsId() {
        com.red_escuela.backend.billing.discount.domain.StudentDiscount existing = mock(com.red_escuela.backend.billing.discount.domain.StudentDiscount.class);
        when(existing.getId()).thenReturn(77L);
        when(existing.getFromPeriod()).thenReturn(YearMonth.of(2027, 5));
        when(existing.getToPeriod()).thenReturn(YearMonth.of(2027, 7));
        when(existing.overlapsRangeOfSameKind(any(), any(), any(), any())).thenReturn(true);
        when(repository.findByProgramEnrollmentIdAndStatus(5L, com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus.ACTIVE)).thenReturn(List.of(existing));

        StudentDiscountGrantPreview preview = preview(individual(InvoiceType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 6), YearMonth.of(2027, 9)), true);

        assertThat(preview.overlappingDiscountId()).isEqualTo(77L);
        assertThat(preview.violations()).extracting(violation -> violation.code()).contains("STUDENT_DISCOUNT_OVERLAP");
    }
}
