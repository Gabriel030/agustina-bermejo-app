package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.AppliedStudentDiscountInstallment;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrant;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountWithUsage;
import com.red_escuela.backend.billing.discount.repository.AppliedStudentDiscountRepository;
import com.red_escuela.backend.billing.discount.repository.LiveMonthlyInstallmentPeriodRepository;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-282")
class StudentDiscountUsageReaderTest {
    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);

    private final AppliedStudentDiscountRepository appliedRepository = mock(AppliedStudentDiscountRepository.class);
    private final LiveMonthlyInstallmentPeriodRepository livePeriodRepository = mock(LiveMonthlyInstallmentPeriodRepository.class);
    private final StudentDiscountUsageReader reader = new StudentDiscountUsageReader(appliedRepository, livePeriodRepository);

    private StudentDiscount discount(EnrollmentReference enrollment, long id) {
        StudentDiscount discount = StudentDiscount.grantStudentDiscount(new StudentDiscountGrant(enrollment, 7L, Currency.ARS, null, DiscountType.SCHOLARSHIP,
                InvoiceType.MONTHLY_INSTALLMENT, new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("50"))), null, 1,
                new ScholarshipPeriodRange(MARCH, YearMonth.of(2027, 6)), "motivo"), 3L, null, Instant.parse("2027-03-01T10:00:00Z"));
        ReflectionTestUtils.setField(discount, "id", id);
        return discount;
    }

    @Test
    void aCourseDiscountReadsTheLivePeriodsOfItsCourseEnrollment() {
        StudentDiscount courseDiscount = discount(EnrollmentReference.ofCourse(20L), 5L);
        when(appliedRepository.findLiveApplicationsOf(List.of(5L))).thenReturn(List.of(new AppliedStudentDiscountInstallment(5L, MARCH, 1, 4)));
        when(livePeriodRepository.findLivePeriodsOfCourseEnrollment(20L)).thenReturn(List.of(MARCH, APRIL));

        List<StudentDiscountWithUsage> usage = reader.readUsageOf(List.of(courseDiscount));

        assertThat(usage.get(0).usage().appliedInstallments()).extracting(AppliedStudentDiscountInstallment::period).containsExactly(MARCH);
        assertThat(usage.get(0).usage().lostPeriods()).containsExactly(APRIL);
        verify(livePeriodRepository, never()).findLivePeriodsOfProgramEnrollment(anyLong());
    }

    @Test
    void aProgramDiscountReadsTheLivePeriodsOfItsProgramEnrollment() {
        StudentDiscount programDiscount = discount(EnrollmentReference.ofProgram(8L), 6L);
        when(livePeriodRepository.findLivePeriodsOfProgramEnrollment(8L)).thenReturn(List.of(MARCH));

        List<StudentDiscountWithUsage> usage = reader.readUsageOf(List.of(programDiscount));

        assertThat(usage.get(0).usage().lostPeriods()).containsExactly(MARCH);
        verify(livePeriodRepository, never()).findLivePeriodsOfCourseEnrollment(anyLong());
    }
}
