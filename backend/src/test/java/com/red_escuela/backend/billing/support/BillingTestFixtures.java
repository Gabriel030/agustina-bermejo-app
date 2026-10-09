package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.ChargeKey;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class BillingTestFixtures {

    private static final StatusChangeActor ACTOR = new StatusChangeActor(null, "billing-test-fixtures");

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
        return inTransaction(() -> {
            Invoice managed = reload(invoice);
            if (managed.getPaymentStatus() == InvoicePaymentStatus.PENDING) {
                managed.markInvoiceAsOverdue(overdueDate, ACTOR);
            }
            bean(InvoiceRepository.class).flush();
            return managed;
        });
    }

    public static Invoice markInvoiceAsOverdue(Invoice invoice) {
        return markInvoiceAsOverdueOn(invoice, invoice.getDueDate().plusDays(1));
    }

    public static Invoice markInvoiceAsPending(Invoice invoice) {
        return invoice;
    }

    public static Invoice payInvoiceInFull(Invoice invoice) {
        return inTransaction(() -> payInTransaction(reload(invoice), invoice.getDueDate()));
    }

    private static Invoice payInTransaction(Invoice invoice, LocalDate paidOn) {
        Long studentId = bean(InvoiceRepository.class).findOwnerStudentIdByInvoiceId(invoice.getId()).orElseThrow();
        Student student = bean(com.red_escuela.backend.identity.repository.StudentRepository.class).findById(studentId).orElseThrow();
        Money amount = invoice.getAmounts().outstandingAmount().value();
        Payment payment = new Payment();
        payment.setAmount(amount);
        payment.setMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setRegisteredBy(student.getPerson());
        payment.setPaidAt(LocalDateTime.now());
        payment = bean(PaymentRepository.class).save(payment);
        invoice.allocateFullInvoicePayment(NonNegativeMoney.of(amount), paidOn, ACTOR);
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPayment(payment);
        allocation.setInvoice(invoice);
        allocation.setAmountApplied(amount);
        payment.getAllocations().add(allocation);
        StudentCurrentAccountService accounts = bean(StudentCurrentAccountService.class);
        StudentCurrentAccount account = accounts.lockStudentCurrentAccount(invoice.getStudentCurrentAccountId());
        accounts.recordPaymentReceivedCredit(account, payment.getId(), NonNegativeMoney.of(amount), paidOn, "Pago de prueba");
        accounts.allocateUnallocatedCreditToInvoicePayment(account, NonNegativeMoney.of(amount));
        bean(InvoiceRepository.class).flush();
        return invoice;
    }

    private static Invoice reload(Invoice invoice) {
        return bean(InvoiceRepository.class).findInvoiceWithLinesById(invoice.getId()).orElseThrow();
    }

    private static <T> T bean(Class<T> type) {
        return BillingTestApplicationContext.bean(type);
    }

    private static <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(bean(org.springframework.transaction.PlatformTransactionManager.class)).execute(status -> work.get());
    }

    public static final class InvoiceFixture {
        private ProgramEnrollment programEnrollment;
        private CourseEnrollment courseEnrollment;
        private com.red_escuela.backend.identity.entity.Student student;
        private FeeType feeType = FeeType.MONTHLY_INSTALLMENT;
        private YearMonth period = YearMonth.now();
        private LocalDate dueDate = LocalDate.now();
        private Money baseAmount = Money.of(java.math.BigDecimal.valueOf(10000), com.red_escuela.backend.shared.domain.Currency.ARS);
        private InvoicePaymentStatus status = InvoicePaymentStatus.PENDING;
        private Consumer<Invoice> customization = invoice -> {
        };

        private InvoiceFixture() {
        }

        public InvoiceFixture forProgramEnrollment(ProgramEnrollment enrollment) {
            this.programEnrollment = enrollment;
            return this;
        }

        public InvoiceFixture forCourseEnrollment(CourseEnrollment enrollment) {
            this.courseEnrollment = enrollment;
            return this;
        }

        public InvoiceFixture forStudent(com.red_escuela.backend.identity.entity.Student student) {
            this.student = student;
            return this;
        }

        public InvoiceFixture ofType(FeeType feeType) {
            this.feeType = feeType;
            return this;
        }

        public InvoiceFixture forPeriod(YearMonth period) {
            this.period = period;
            return this;
        }

        public InvoiceFixture dueOn(LocalDate dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public InvoiceFixture withBaseAmount(Money amount) {
            this.baseAmount = amount;
            return this;
        }

        public InvoiceFixture pending() {
            return inState(InvoicePaymentStatus.PENDING);
        }

        public InvoiceFixture paid() {
            return inState(InvoicePaymentStatus.PAID);
        }

        public InvoiceFixture overdue() {
            return inState(InvoicePaymentStatus.OVERDUE);
        }

        public InvoiceFixture inState(InvoicePaymentStatus status) {
            this.status = status;
            return this;
        }

        public InvoiceFixture customizedBy(Consumer<Invoice> customization) {
            this.customization = customization;
            return this;
        }

        public Invoice build() {
            return inTransaction(this::issue);
        }

        private Invoice issue() {
            bean(jakarta.persistence.EntityManager.class).flush();
            Long studentId = (student != null ? student : programEnrollment != null ? programEnrollment.getStudent() : courseEnrollment.getStudent()).getId();
            var person = bean(com.red_escuela.backend.identity.repository.StudentRepository.class).findById(studentId).orElseThrow().getPerson();
            StudentCurrentAccount account = bean(StudentCurrentAccountService.class).findOrOpenAndLockStudentCurrentAccount(studentId, baseAmount.currency());
            Long priceRevisionId = priceRevisionId(person.getId());
            ChargeKey key = new ChargeKey("FX:" + UUID.randomUUID());
            InvoiceType invoiceType = InvoiceType.valueOf(feeType.name());
            InvoiceIssuingReason reason = feeType == FeeType.MONTHLY_INSTALLMENT ? InvoiceIssuingReason.MONTHLY_RUN
                    : feeType == FeeType.ENROLLMENT_FEE ? InvoiceIssuingReason.ENROLLMENT_FEE_ON_DEMAND : InvoiceIssuingReason.AD_HOC_CHARGE;
            InvoiceDraftHeader header = new InvoiceDraftHeader(account.getId(), baseAmount.currency(), InvoiceDocumentType.INVOICE, invoiceType, reason, null);
            InvoiceLineDraft line = new InvoiceLineDraft(feeType, feeType == FeeType.MONTHLY_INSTALLMENT ? "Cuota " + period : feeType.name() + " " + period, period,
                    programEnrollment == null ? null : programEnrollment.getId(), courseEnrollment == null ? null : courseEnrollment.getId(), null, null,
                    priceRevisionId, key.value(), NonNegativeMoney.of(baseAmount));
            Invoice draft = switch (invoiceType) {
                case MONTHLY_INSTALLMENT -> InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header, line, Optional.empty(), ACTOR);
                case ENROLLMENT_FEE -> InvoiceFactory.createEnrollmentFeeInvoiceDraft(header, line, Optional.empty(), ACTOR);
                case EXAM_FEE -> InvoiceFactory.createExamFeeInvoiceDraft(header, line, ACTOR);
                case RETAKEN_SUBJECT_FEE -> InvoiceFactory.createRetakenSubjectFeeInvoiceDraft(header, line, ACTOR);
                case DEGREE_ISSUANCE_FEE -> InvoiceFactory.createDegreeIssuanceFeeInvoiceDraft(header, line, ACTOR);
                case ADMINISTRATIVE_FEE -> InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
                default -> throw new IllegalStateException("Unsupported fixture type " + invoiceType);
            };
            draft.issueInvoice(bean(InvoiceNumberGenerator.class).nextInvoiceNumber(InvoiceDocumentType.INVOICE), dueDate.isAfter(LocalDate.now()) ? LocalDate.now() : dueDate,
                    dueDate, new StudentSnapshot(person.getName() == null ? "Alumno de prueba" : person.getName() + " " + person.getLastname(), person.getDni() == null ? "00000000" : person.getDni(), null), ACTOR);
            Invoice saved = bean(InvoiceRepository.class).save(draft);
            bean(InvoiceRepository.class).flush();
            bean(StudentCurrentAccountService.class).recordInvoiceIssuedDebit(account, saved, saved.getIssueDate());
            Invoice result = saved;
            if (status == InvoicePaymentStatus.OVERDUE) {
                result.markInvoiceAsOverdue(dueDate.plusDays(1), ACTOR);
            } else if (status == InvoicePaymentStatus.PAID) {
                result = payInTransaction(result, dueDate);
            } else if (status != InvoicePaymentStatus.PENDING) {
                throw new UnsupportedOperationException("The new billing model has no direct " + status + " state (D-165, D-136)");
            }
            customization.accept(result);
            bean(InvoiceRepository.class).flush();
            return result;
        }

        private Long priceRevisionId(Long createdById) {
            if (feeType != FeeType.MONTHLY_INSTALLMENT && feeType != FeeType.ENROLLMENT_FEE) {
                return null;
            }
            JdbcTemplate jdbc = bean(JdbcTemplate.class);
            Long programId = programEnrollment == null ? null : programEnrollment.getProgram().getId();
            Long courseId = courseEnrollment == null ? null : courseEnrollment.getCourse().getId();
            java.util.List<Long> found = jdbc.queryForList("select id from price_revision where " + (programId != null ? "program_id = ?" : "course_id = ?")
                    + " order by id limit 1", Long.class, programId != null ? programId : courseId);
            if (!found.isEmpty()) {
                return found.get(0);
            }
            return jdbc.queryForObject("insert into price_revision (program_id, course_id, fee_type, description, monthly_amount, monthly_amount_currency, effective_from, created_by_id, created_at) "
                    + "values (?, ?, ?, 'Fixture', ?, ?, date '2000-01-01', ?, now()) returning id", Long.class, programId, courseId, feeType.name(), baseAmount.amount(), baseAmount.currency().name(), createdById);
        }
    }
}
