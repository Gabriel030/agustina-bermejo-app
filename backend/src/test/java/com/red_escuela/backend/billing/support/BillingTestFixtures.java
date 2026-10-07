package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Consumer;

public final class BillingTestFixtures {
    private BillingTestFixtures() {
    }

    public static InvoiceFixture invoice() {
        return new InvoiceFixture();
    }

    public static Invoice issueProgramMonthlyInstallment(ProgramEnrollment enrollment, YearMonth period, Money amount) {
        return invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(period).dueOn(period.atDay(1)).withBaseAmount(amount).pending().build();
    }

    public static Invoice issueCourseMonthlyInstallment(CourseEnrollment enrollment, YearMonth period, Money amount) {
        return invoice().forCourseEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(period).dueOn(period.atDay(1)).withBaseAmount(amount).pending().build();
    }

    public static Invoice issueEnrollmentFee(ProgramEnrollment enrollment, YearMonth period, Money amount) {
        return invoice().forProgramEnrollment(enrollment).ofType(FeeType.ENROLLMENT_FEE).forPeriod(period).dueOn(period.atDay(1)).withBaseAmount(amount).pending().build();
    }

    public static Invoice markInvoiceAsOverdueOn(Invoice invoice, LocalDate overdueDate) {
        invoice.setDueDate(overdueDate.minusDays(1));
        invoice.setStatus(InvoiceStatus.OVERDUE);
        return invoice;
    }

    public static Invoice markInvoiceAsOverdue(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.OVERDUE);
        return invoice;
    }

    public static Invoice markInvoiceAsPending(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.PENDING);
        return invoice;
    }

    public static Invoice cancelInvoice(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return invoice;
    }

    public static Invoice waiveInvoice(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.WAIVED);
        return invoice;
    }

    public static Invoice payInvoiceInFull(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.PAID);
        return invoice;
    }

    public static final class InvoiceFixture {
        private final Invoice invoice = new Invoice();

        private InvoiceFixture() {
            invoice.setFeeType(FeeType.MONTHLY_INSTALLMENT);
            invoice.setStatus(InvoiceStatus.PENDING);
        }

        public InvoiceFixture forProgramEnrollment(ProgramEnrollment enrollment) {
            invoice.setProgramEnrollment(enrollment);
            return this;
        }

        public InvoiceFixture forCourseEnrollment(CourseEnrollment enrollment) {
            invoice.setCourseEnrollment(enrollment);
            return this;
        }

        public InvoiceFixture ofType(FeeType feeType) {
            invoice.setFeeType(feeType);
            return this;
        }

        public InvoiceFixture forPeriod(YearMonth period) {
            invoice.setPeriod(period);
            return this;
        }

        public InvoiceFixture dueOn(LocalDate dueDate) {
            invoice.setDueDate(dueDate);
            return this;
        }

        public InvoiceFixture withBaseAmount(Money amount) {
            invoice.setBaseAmount(amount);
            return this;
        }

        public InvoiceFixture pending() {
            return inState(InvoiceStatus.PENDING);
        }

        public InvoiceFixture paid() {
            return inState(InvoiceStatus.PAID);
        }

        public InvoiceFixture overdue() {
            return inState(InvoiceStatus.OVERDUE);
        }

        public InvoiceFixture cancelled() {
            return inState(InvoiceStatus.CANCELLED);
        }

        public InvoiceFixture waived() {
            return inState(InvoiceStatus.WAIVED);
        }

        public InvoiceFixture inState(InvoiceStatus status) {
            invoice.setStatus(status);
            return this;
        }

        public InvoiceFixture customizedBy(Consumer<Invoice> customization) {
            customization.accept(invoice);
            return this;
        }

        public Invoice build() {
            return invoice;
        }
    }
}
