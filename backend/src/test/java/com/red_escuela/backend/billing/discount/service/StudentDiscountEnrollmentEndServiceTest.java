package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.ScholarshipPeriodRange;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrant;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.discount.repository.StudentDiscountRepository;
import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentDiscountEnrollmentEndServiceTest {
    private static final Instant NOW = Instant.parse("2027-05-10T10:00:00Z");
    private static final Set<StudentDiscountStatus> ACTIVE_ONLY = Set.of(StudentDiscountStatus.ACTIVE);

    private final StudentDiscountRepository repository = mock(StudentDiscountRepository.class);
    private final EnrollmentDiscountLock lock = mock(EnrollmentDiscountLock.class);
    private final StudentDiscountEnrollmentEndService service = new StudentDiscountEnrollmentEndService(repository, lock, Clock.fixed(NOW, ZoneOffset.UTC));

    private StudentDiscount activeDiscount(EnrollmentReference enrollment) {
        return StudentDiscount.grantStudentDiscount(new StudentDiscountGrant(enrollment, 7L, Currency.ARS, null, DiscountType.SCHOLARSHIP, InvoiceType.MONTHLY_INSTALLMENT,
                new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("50"))), null, 1,
                new ScholarshipPeriodRange(YearMonth.of(2027, 3), YearMonth.of(2027, 12)), "motivo"), 3L, null, NOW);
    }

    @Test
    void aCourseWithdrawalLocksTheCourseEnrollmentAndEndsItsActiveDiscounts() {
        StudentDiscount first = activeDiscount(EnrollmentReference.ofCourse(20L));
        StudentDiscount second = activeDiscount(EnrollmentReference.ofCourse(20L));
        when(repository.findByCourseEnrollmentIdAndStatusInForUpdate(20L, ACTIVE_ONLY)).thenReturn(List.of(first, second));

        service.endActiveDiscountsOf(WithdrawnEnrollmentFacts.ofCourseEnrollment(7L, 20L, LocalDate.of(2027, 5, 9), LocalDate.of(2027, 5, 10), WithdrawalCause.VOLUNTARY));

        assertThat(first.getStatus()).isEqualTo(StudentDiscountStatus.ENDED);
        assertThat(second.getStatus()).isEqualTo(StudentDiscountStatus.ENDED);
        var order = inOrder(lock, repository);
        order.verify(lock).lockDiscountsOf(EnrollmentReference.ofCourse(20L));
        order.verify(repository).findByCourseEnrollmentIdAndStatusInForUpdate(20L, ACTIVE_ONLY);
        order.verify(repository).saveAll(List.of(first, second));
        verify(repository, never()).findByProgramEnrollmentIdAndStatusInForUpdate(anyLong(), anyCollection());
    }

    @Test
    void aProgramGraduationLocksTheProgramEnrollmentAndRecordsAProcessActor() {
        StudentDiscount discount = activeDiscount(EnrollmentReference.ofProgram(5L));
        when(repository.findByProgramEnrollmentIdAndStatusInForUpdate(5L, ACTIVE_ONLY)).thenReturn(List.of(discount));

        service.endActiveDiscountsOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(7L, 5L, LocalDate.of(2027, 5, 9), LocalDate.of(2027, 5, 10), WithdrawalCause.GRADUATION));

        verify(lock).lockDiscountsOf(EnrollmentReference.ofProgram(5L));
        assertThat(discount.getStatus()).isEqualTo(StudentDiscountStatus.ENDED);
        var change = discount.getStatusChanges().get(discount.getStatusChanges().size() - 1);
        assertThat(change.getActorProcess()).isEqualTo("ENROLLMENT_ENDED");
        assertThat(change.getActorPersonId()).isNull();
        assertThat(change.getChangedAt()).isEqualTo(NOW);
    }

    @Test
    void anEnrollmentWithoutActiveDiscountsEndsNothing() {
        when(repository.findByProgramEnrollmentIdAndStatusInForUpdate(5L, ACTIVE_ONLY)).thenReturn(List.of());

        service.endActiveDiscountsOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(7L, 5L, LocalDate.of(2027, 5, 9), LocalDate.of(2027, 5, 10), WithdrawalCause.NONPAYMENT));

        verify(lock).lockDiscountsOf(EnrollmentReference.ofProgram(5L));
    }
}
