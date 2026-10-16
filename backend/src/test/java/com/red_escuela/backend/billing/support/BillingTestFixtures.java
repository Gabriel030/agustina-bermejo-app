package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
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
        return inTransaction(() -> payInTransaction(reload(invoice), invoice.getDueDate(), invoice.getDueDate()));
    }

    public static Invoice payInvoiceInFullWithMoneyOf(Invoice invoice, LocalDate effectiveDate, LocalDate allocatedOn) {
        return inTransaction(() -> payInTransaction(reload(invoice), effectiveDate, allocatedOn));
    }

    public static Long registerApprovedGatewayPayment(Long invoiceId, Money amount) {
        return bean(com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort.class).registerApprovedMercadoPagoPayment(
                invoiceId, amount, 1, null, "mp-fixture-" + UUID.randomUUID());
    }

    public static Long registerApprovedBankTransferPayment(Long invoiceId, Long reviewerPersonId) {
        return bean(com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort.class).registerApprovedBankTransferPayment(invoiceId, reviewerPersonId);
    }

    private static Invoice payInTransaction(Invoice invoice, LocalDate effectiveDate, LocalDate paidOn) {
        Money amount = invoice.getAmounts().outstandingAmount().value();
        StudentCurrentAccountService accounts = bean(StudentCurrentAccountService.class);
        StudentCurrentAccount account = accounts.lockStudentCurrentAccount(invoice.getStudentCurrentAccountId());
        com.red_escuela.backend.billing.payment.domain.Payment payment = bean(com.red_escuela.backend.billing.payment.repository.PaymentRepository.class).save(
                com.red_escuela.backend.billing.payment.domain.Payment.registerApprovedCashPayment(account, NonNegativeMoney.of(amount), paidOn,
                        new com.red_escuela.backend.billing.payment.domain.CashPaymentDetails(null), UUID.randomUUID().toString(), ACTOR));
        invoice.allocateFullInvoicePayment(NonNegativeMoney.of(amount), paidOn, ACTOR);
        com.red_escuela.backend.billing.account.domain.CurrentAccountEntry credit = accounts.recordPaymentReceivedCredit(account, payment.getId(),
                NonNegativeMoney.of(amount), paidOn, "Pago de prueba");
        bean(com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository.class).saveWithSources(
                com.red_escuela.backend.billing.account.domain.PaymentAllocation.recordFullInvoiceAllocation(invoice, NonNegativeMoney.of(amount), effectiveDate, paidOn, ACTOR),
                java.util.List.of(new com.red_escuela.backend.billing.account.domain.AllocationSourceShare(credit.getId(), payment.getId(), null, NonNegativeMoney.of(amount))));
        accounts.consumeUnallocatedCreditForAllocation(account, NonNegativeMoney.of(amount));
        bean(InvoiceRepository.class).flush();
        return invoice;
    }

    public static long seedApprovedPaymentRow(Long accountId) {
        return seedApprovedPaymentRow(accountId, java.math.BigDecimal.ONE);
    }

    public static long seedApprovedPaymentRow(Long accountId, NonNegativeMoney amount) {
        return seedApprovedPaymentRow(accountId, amount.value().amount());
    }

    private static long seedApprovedPaymentRow(Long accountId, java.math.BigDecimal amount) {
        return bean(org.springframework.jdbc.core.JdbcTemplate.class).queryForObject(
                "insert into payment (student_current_account_id, currency, status, gateway, method_type, amount, amount_currency, approved_amount, "
                        + "approved_amount_currency, paid_on, refunded_amount, refunded_amount_currency, registered_at, started_at, method_details, registered_by_process) "
                        + "select a.id, a.currency, 'APPROVED', 'MANUAL', 'CASH', ?, a.currency, ?, a.currency, current_date, 0, a.currency, now(), now(), "
                        + "cast('{\"type\":\"CASH\"}' as jsonb), 'TEST' from student_current_account a where a.id = ? returning id",
                Long.class, amount, amount, accountId);
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
        private Money scholarshipAmount;
        private Money marketingDiscountAmount;
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

        public InvoiceFixture withScholarshipOf(Money scholarshipAmount) {
            this.scholarshipAmount = scholarshipAmount;
            return this;
        }

        public InvoiceFixture withMarketingDiscountOf(Money marketingDiscountAmount) {
            this.marketingDiscountAmount = marketingDiscountAmount;
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
            Long priceVersionId = priceVersionId(person.getId());
            ChargeKey key = new ChargeKey("FX:" + UUID.randomUUID());
            InvoiceType invoiceType = InvoiceType.valueOf(feeType.name());
            InvoiceIssuingReason reason = feeType == FeeType.MONTHLY_INSTALLMENT ? InvoiceIssuingReason.MONTHLY_RUN
                    : feeType == FeeType.ENROLLMENT_FEE ? InvoiceIssuingReason.ENROLLMENT_FEE_ON_DEMAND : InvoiceIssuingReason.AD_HOC_CHARGE;
            InvoiceDraftHeader header = new InvoiceDraftHeader(account.getId(), baseAmount.currency(), InvoiceDocumentType.INVOICE, invoiceType, reason, null);
            InvoiceLineDraft line = new InvoiceLineDraft(feeType, feeType == FeeType.MONTHLY_INSTALLMENT ? "Cuota " + period : feeType.name() + " " + period, period,
                    programEnrollment == null ? null : programEnrollment.getId(), courseEnrollment == null ? null : courseEnrollment.getId(), null, null,
                    priceVersionId, key.value(), NonNegativeMoney.of(baseAmount), null, null,
                    marketingDiscountAmount == null ? null : NonNegativeMoney.of(marketingDiscountAmount));
            Invoice draft = switch (invoiceType) {
                case MONTHLY_INSTALLMENT -> InvoiceFactory.createMonthlyInstallmentInvoiceDraft(header, line, Optional.empty(), ACTOR);
                case ENROLLMENT_FEE -> InvoiceFactory.createEnrollmentFeeInvoiceDraft(header, line, Optional.empty(), ACTOR);
                case EXAM_FEE -> InvoiceFactory.createExamFeeInvoiceDraft(header, line, ACTOR);
                case RETAKEN_SUBJECT_FEE -> InvoiceFactory.createRetakenSubjectFeeInvoiceDraft(header, line, ACTOR);
                case DEGREE_ISSUANCE_FEE -> InvoiceFactory.createDegreeIssuanceFeeInvoiceDraft(header, line, ACTOR);
                case ADMINISTRATIVE_FEE -> InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
                default -> throw new IllegalStateException("Unsupported fixture type " + invoiceType);
            };
            if (scholarshipAmount != null) {
                draft.addDiscountToDraftLine(1, new com.red_escuela.backend.billing.invoice.domain.InvoiceDiscountDraft(
                        com.red_escuela.backend.billing.invoice.domain.DiscountType.SCHOLARSHIP, "Beca de prueba", null, NonNegativeMoney.of(baseAmount),
                        NonNegativeMoney.of(scholarshipAmount), null, null));
            }
            draft.issueInvoice(bean(InvoiceNumberGenerator.class).nextInvoiceNumber(InvoiceDocumentType.INVOICE), dueDate.isAfter(LocalDate.now()) ? LocalDate.now() : dueDate,
                    dueDate, new StudentSnapshot(person.getName() == null ? "Alumno de prueba" : person.getName() + " " + person.getLastname(), person.getDni() == null ? "00000000" : person.getDni(), null), ACTOR);
            Invoice saved = bean(InvoiceRepository.class).save(draft);
            bean(InvoiceRepository.class).flush();
            bean(StudentCurrentAccountService.class).recordInvoiceIssuedDebit(account, saved, saved.getIssueDate());
            Invoice result = saved;
            if (status == InvoicePaymentStatus.OVERDUE) {
                result.markInvoiceAsOverdue(dueDate.plusDays(1), ACTOR);
            } else if (status == InvoicePaymentStatus.PAID) {
                result = payInTransaction(result, dueDate, dueDate);
            } else if (status != InvoicePaymentStatus.PENDING) {
                throw new UnsupportedOperationException("The new billing model has no direct " + status + " state (D-165, D-136)");
            }
            customization.accept(result);
            bean(InvoiceRepository.class).flush();
            return result;
        }

        private Long priceVersionId(Long createdById) {
            if (feeType != FeeType.MONTHLY_INSTALLMENT && feeType != FeeType.ENROLLMENT_FEE) {
                return null;
            }
            JdbcTemplate jdbc = bean(JdbcTemplate.class);
            Long programId = programEnrollment == null ? null : programEnrollment.getProgram().getId();
            Long courseId = courseEnrollment == null ? null : courseEnrollment.getCourse().getId();
            if (programId == null && courseId == null) {
                programId = jdbc.queryForObject("insert into program (name, status, total_years, currency) values (?, 'ACTIVE', 1, ?) returning id", Long.class,
                        "Fixture " + System.nanoTime(), baseAmount.currency().name());
            }
            if (marketingDiscountAmount != null) {
                return PriceFixtures.persistPriceVersion(programId, courseId, feeType, "Fixture con marketing", baseAmount.plus(marketingDiscountAmount),
                        java.time.LocalDate.of(2000, 1, 1), createdById, "FIXED", marketingDiscountAmount.amount());
            }
            java.util.List<Long> found = jdbc.queryForList("select v.id from price_version v join price p on p.id = v.price_id where "
                    + (programId != null ? "p.program_id = ?" : "p.course_id = ?") + " order by v.id limit 1", Long.class, programId != null ? programId : courseId);
            if (!found.isEmpty()) {
                return found.get(0);
            }
            return PriceFixtures.persistPriceVersion(programId, courseId, feeType, "Fixture", baseAmount, java.time.LocalDate.of(2000, 1, 1), createdById);
        }
    }
}
