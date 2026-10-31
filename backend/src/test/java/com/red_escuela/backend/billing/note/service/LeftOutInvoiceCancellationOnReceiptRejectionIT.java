package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
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
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaymentReviewReleasedEvent;
import com.red_escuela.backend.billing.planning.TestTemplateTerms;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.enrollment.api.WithdrawalFactsFinder;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-311")
@Tag("D-300")
class LeftOutInvoiceCancellationOnReceiptRejectionIT extends AbstractInvoiceCancellationIT {
    private static final LocalDate WITHDRAWAL_DAY = LocalDate.of(2027, 11, 20);

    @Autowired
    private LeftOutInvoiceCancellationOnReceiptRejectionListener cancellation;
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private WithdrawalFactsFinder withdrawalFacts;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private ProgramEnrollment newProgramEnrollment(Long studentId) {
        Student student = studentRepository.findById(studentId).orElseThrow();
        Program program = new Program();
        program.setName("Carrera de baja " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(programRepository.save(program));
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        return programEnrollmentRepository.save(enrollment);
    }

    private void putUnderReview(Long invoiceId) {
        inTransaction(() -> {
            invoiceRepository.findById(invoiceId).orElseThrow().markPaymentUnderReview(WITHDRAWAL_DAY.minusDays(1), ACTOR, "Comprobante subido");
            return null;
        });
    }

    private void releaseReview(Long invoiceId) {
        inTransaction(() -> {
            invoiceRepository.findById(invoiceId).orElseThrow().releasePaymentReviewWithoutLateTerms(WITHDRAWAL_DAY, ACTOR, "Comprobante rechazado");
            return null;
        });
    }

    private void withdraw(ProgramEnrollment enrollment, LocalDate day) {
        MutableClock.INSTANCE.setDate(day);
        Long staffPersonId = newStaff("ADMINISTRATIVO").personId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(staffPersonId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATIVO"))));
        programEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(null, staffPersonId));
    }

    private boolean cancelAfterRejection(Long invoiceId) {
        return inTransaction(() -> cancellation.cancelIfLeftOutOfWithdrawal(invoiceId));
    }

    @Test
    @Tag("C-34")
    void theNextYearEnrollmentFeeUnderReviewAtTheWithdrawalIsCancelledByTheSystemWhenItsReceiptIsRejected() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        putUnderReview(fee);

        withdraw(enrollment, WITHDRAWAL_DAY);

        assertThat(paymentStatusOf(fee)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(creditNoteCountOf(fee)).isZero();

        releaseReview(fee);
        assertThat(cancelAfterRejection(fee)).isTrue();

        assertThat(paymentStatusOf(fee)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForMap("select issued_by_process, issued_by_person_id, authorization_request_id from credit_note where invoice_id = ?", fee))
                .containsEntry("issued_by_process", "REJECTED_WITHDRAWAL_RECEIPT_CANCELLATION").containsEntry("issued_by_person_id", null)
                .containsEntry("authorization_request_id", null);
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ? and charge_key_released_at is not null", Long.class, fee)).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void cancellingTwiceLeavesASingleCreditNote() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        putUnderReview(fee);
        withdraw(enrollment, WITHDRAWAL_DAY);
        releaseReview(fee);

        boolean first = cancelAfterRejection(fee);
        boolean second = cancelAfterRejection(fee);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(creditNoteCountOf(fee)).isEqualTo(1L);
    }

    @Test
    void theRejectionOfAnEnrollmentFeeOfAnEnrollmentThatIsStillActiveCancelsNothing() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        putUnderReview(fee);
        releaseReview(fee);

        assertThat(cancelAfterRejection(fee)).isFalse();

        assertThat(paymentStatusOf(fee)).isEqualTo("PENDING");
        assertThat(creditNoteCountOf(fee)).isZero();
    }

    @Test
    @Tag("D-312")
    void anEnrollmentFeeOfATermThatAlreadyStartedIsNotCancelledWhenItsReceiptIsRejected() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        putUnderReview(fee);
        withdraw(enrollment, LocalDate.of(2028, 3, 1));
        releaseReview(fee);

        assertThat(cancelAfterRejection(fee)).isFalse();

        assertThat(paymentStatusOf(fee)).isEqualTo("PENDING");
        assertThat(creditNoteCountOf(fee)).isZero();
    }

    @Test
    void anInvoiceStillUnderReviewIsNotCancelledUntilItsReceiptIsDecided() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        putUnderReview(fee);
        withdraw(enrollment, WITHDRAWAL_DAY);

        assertThat(cancelAfterRejection(fee)).isFalse();

        assertThat(paymentStatusOf(fee)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(creditNoteCountOf(fee)).isZero();
    }

    @Test
    void aMonthlyInstallmentAfterTheWithdrawalMonthIsNotCancelledByTheSystemWhenItsReceiptIsRejected() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Invoice june = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.of(2027, 6))
                .dueOn(LocalDate.of(2027, 6, 10)).withBaseAmount(ars("80000.00").value()).build();
        inTransaction(() -> {
            invoiceRepository.findById(june.getId()).orElseThrow().markPaymentUnderReview(LocalDate.of(2027, 5, 14), ACTOR, "Comprobante subido");
            return null;
        });
        withdraw(enrollment, LocalDate.of(2027, 5, 15));
        inTransaction(() -> {
            invoiceRepository.findById(june.getId()).orElseThrow().releasePaymentReviewWithoutLateTerms(LocalDate.of(2027, 5, 16), ACTOR, "Comprobante rechazado");
            return null;
        });

        assertThat(cancelAfterRejection(june.getId())).isFalse();

        assertThat(paymentStatusOf(june.getId())).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-247")
    void anInvoiceWithAPendingDirectorRequestIsNotCancelledByTheSystemWhenItsReceiptIsRejected() throws Exception {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        long requestId = pendingRequestId(requestCancellation(fee, newStaff("ADMINISTRATIVO"), cancellationBody("CANCELLATION", "Pedido previo"), 202));
        putUnderReview(fee);
        withdraw(enrollment, WITHDRAWAL_DAY);
        releaseReview(fee);

        assertThat(cancelAfterRejection(fee)).isFalse();

        assertThat(paymentStatusOf(fee)).isEqualTo("PENDING");
        assertThat(creditNoteCountOf(fee)).isZero();
        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-246")
    void aWithdrawalForNonpaymentNeverCancelsTheInvoiceWhoseReceiptWasRejected() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        putUnderReview(fee);
        MutableClock.INSTANCE.setDate(WITHDRAWAL_DAY);
        programEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.forNonpayment());
        releaseReview(fee);

        assertThat(withdrawalFacts.findVoluntaryWithdrawalOfProgramEnrollment(enrollment.getId())).isEmpty();
        assertThat(cancelAfterRejection(fee)).isFalse();

        assertThat(paymentStatusOf(fee)).isEqualTo("PENDING");
        assertThat(creditNoteCountOf(fee)).isZero();
    }

    @Test
    void aVoluntarilyWithdrawnCourseEnrollmentIsFoundByTheLookupAndItsInvoiceIsNotCancelledByTheSystem() {
        Long studentId = newStudentId();
        Long courseEnrollmentId = inTransaction(() -> {
            Term term = new Term();
            term.setYear(2027);
            term.setDescription("2027-1C " + System.nanoTime());
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setTerm(term);
            course.setName("Curso de baja " + System.nanoTime());
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            CourseEnrollment courseEnrollment = new CourseEnrollment();
            courseEnrollment.setStudent(studentRepository.findById(studentId).orElseThrow());
            courseEnrollment.setCourse(course);
            courseEnrollment.setStatus(EnrollmentStatus.ENROLLED);
            courseEnrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
            return courseEnrollmentRepository.save(courseEnrollment).getId();
        });
        Invoice june = BillingTestFixtures.invoice().forCourseEnrollment(courseEnrollmentRepository.findById(courseEnrollmentId).orElseThrow())
                .ofType(FeeType.MONTHLY_INSTALLMENT).forPeriod(YearMonth.of(2027, 6)).dueOn(LocalDate.of(2027, 6, 10)).withBaseAmount(ars("5000.00").value()).build();
        jdbc.update("update course_enrollment set status = 'WITHDRAWN', withdrawal_effective_date = ?, withdrawal_registered_on = ?, withdrawn_by_person_id = ? where id = ?",
                LocalDate.of(2027, 5, 15), LocalDate.of(2027, 5, 15), newStaff("ADMINISTRATIVO").personId(), courseEnrollmentId);

        assertThat(withdrawalFacts.findVoluntaryWithdrawalOfCourseEnrollment(courseEnrollmentId)).isPresent();
        assertThat(cancelAfterRejection(june.getId())).isFalse();

        assertThat(paymentStatusOf(june.getId())).isEqualTo("PENDING");
        assertThat(creditNoteCountOf(june.getId())).isZero();
    }

    @Test
    void aWithdrawalConcurrentWithTheReleaseOfTheReceiptLeavesASingleCreditNote() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        putUnderReview(fee);
        MutableClock.INSTANCE.setDate(WITHDRAWAL_DAY);
        Long staffPersonId = newStaff("ADMINISTRATIVO").personId();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> withdrawing = executor.submit(() -> {
            start.await();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(staffPersonId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATIVO"))));
            try {
                programEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(null, staffPersonId));
            } finally {
                SecurityContextHolder.clearContext();
            }
            return null;
        });
        Future<?> rejecting = executor.submit(() -> {
            start.await();
            releaseReview(fee);
            cancelAfterRejection(fee);
            return null;
        });
        start.countDown();
        withdrawing.get(60, TimeUnit.SECONDS);
        rejecting.get(60, TimeUnit.SECONDS);
        executor.shutdownNow();
        cancelAfterRejection(fee);

        assertThat(paymentStatusOf(fee)).isEqualTo("CANCELLED");
        assertThat(creditNoteCountOf(fee)).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void thePublishedReleaseEventTriggersTheCancellationAfterCommit() {
        ProgramEnrollment enrollment = newProgramEnrollment(newStudentId());
        Long fee = enrollmentFeeOf(enrollment, templateStartingOn(enrollment.getProgram(), LocalDate.of(2028, 3, 1)), 2028);
        putUnderReview(fee);
        withdraw(enrollment, WITHDRAWAL_DAY);
        releaseReview(fee);

        inTransaction(() -> {
            eventPublisher.publishEvent(new InvoicePaymentReviewReleasedEvent(fee, WITHDRAWAL_DAY));
            return null;
        });

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(paymentStatusOf(fee)).isEqualTo("CANCELLED"));
        assertThat(creditNoteCountOf(fee)).isEqualTo(1L);
    }
}
