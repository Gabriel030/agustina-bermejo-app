package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.service.AdmissionEnrollmentStalledNotifier;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.ApproveProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RejectProgramEnrollmentApplicationCommand;
import com.red_escuela.backend.enrollment.usecase.programenrollmentapplication.RejectProgramEnrollmentApplicationUseCase;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class AdmissionP1ProgramCharacterizationTest extends AdmissionP1CharacterizationSupport {

    @Autowired private ApproveProgramEnrollmentApplicationUseCase approveUseCase;
    @Autowired private RejectProgramEnrollmentApplicationUseCase rejectUseCase;
    @Autowired private ProgramEnrollmentService programEnrollmentService;
    @Autowired private AdmissionEnrollmentStalledNotifier stalledNotifier;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private Long approve(ProgramEnrollmentApplication application) {
        Person preceptor = persistStaff("PRECEPTOR");
        return transactionTemplate.execute(status ->
                approveUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor))
                        .getAdmissionInvoice().getId());
    }

    private void payAdmission(Long invoiceId) {
        BillingTestFixtures.registerApprovedBankTransferPayment(invoiceId, persistStaff("PRECEPTOR").getId());
    }

    private void awaitEnrolled(ProgramEnrollmentApplication application) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(activeEnrollmentsOf(application)).hasSize(1));
    }

    private ProgramEnrollmentApplicationStatus statusOf(ProgramEnrollmentApplication application) {
        return applicationRepository.findById(application.getId()).orElseThrow().getStatus();
    }

    private Map<String, Object> invoiceRow(Long invoiceId) {
        return jdbc.queryForMap("select total, currency, payment_status from invoice where id = ?", invoiceId);
    }

    @Test
    void requestingAProgramApplicationLeavesItPendingWithoutInvoiceNorEnrollment() {
        Student student = persistConfirmedStudent();
        OpenCohort cohort = persistOpenCohort(true);

        ProgramEnrollmentApplication application = requestApplication(student, cohort);

        ProgramEnrollmentApplication stored = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.PENDING);
        assertThat(jdbc.queryForObject("select admission_invoice_id from program_enrollment_application where id = ?", Long.class, application.getId())).isNull();
        assertThat(invoicesOf(student)).isZero();
        assertThat(programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId())).isEmpty();
    }

    @Test
    void approvingIssuesTheAdmissionInvoiceInArsAndKeepsTheApplicationApprovedWithoutEnrolling() {
        Student student = persistConfirmedStudent();
        ProgramEnrollmentApplication application = requestApplication(student, persistOpenCohort(true));

        Long invoiceId = approve(application);

        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        assertThat(jdbc.queryForObject("select admission_invoice_id from program_enrollment_application where id = ?", Long.class, application.getId())).isEqualTo(invoiceId);
        Map<String, Object> invoice = invoiceRow(invoiceId);
        assertThat((BigDecimal) invoice.get("total")).isEqualByComparingTo(ADMISSION_FEE);
        assertThat(invoice.get("currency")).isEqualTo("ARS");
        assertThat(invoice.get("payment_status")).isEqualTo("PENDING");
        FeeType feeType = transactionTemplate.execute(status -> invoiceRepository.findById(invoiceId).orElseThrow().getFeeType());
        assertThat(feeType).isEqualTo(FeeType.ENROLLMENT_FEE);
        assertThat(activeEnrollmentsOf(application)).isEmpty();
        assertThat(programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId())).isEmpty();
    }

    @Test
    void payingTheAdmissionInvoiceEnrollsTheStudentAndLinksTheInvoiceThroughTheAsyncListener() {
        Student student = persistConfirmedStudent();
        ProgramEnrollmentApplication application = requestApplication(student, persistOpenCohort(true));
        Long invoiceId = approve(application);

        payAdmission(invoiceId);

        awaitEnrolled(application);
        ProgramEnrollment enrollment = activeEnrollmentsOf(application).get(0);
        assertThat(enrollment.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
        assertThat(programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId())).contains(enrollment.getId());
        assertThat(invoiceRow(invoiceId).get("payment_status")).isEqualTo("PAID");
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
        Optional<Long> linkedEnrollmentId = transactionTemplate.execute(status ->
                invoiceRepository.findById(invoiceId).orElseThrow().findProgramEnrollmentId());
        assertThat(linkedEnrollmentId).contains(enrollment.getId());
    }

    @Test
    void afterTheCommitBothListenersConsumeThePaidEventAndTheCompletedPublicationsAreDeleted() {
        ProgramEnrollmentApplication application = requestApplication(persistConfirmedStudent(), persistOpenCohort(true));
        Long invoiceId = approve(application);

        payAdmission(invoiceId);

        awaitEnrolled(application);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(publicationsOf(invoiceId)).isZero());
    }

    @Test
    void aListenerFailureLeavesThePaymentCommittedTheApplicationApprovedAndNoEnrollmentPersisted() {
        ProgramEnrollmentApplication application = requestApplication(persistConfirmedStudent(), persistOpenCohort(true));
        Long invoiceId = approve(application);
        doThrow(new IllegalStateException("simulated technical failure")).when(programEnrollmentService).enrollApplicantAfterAdmissionPayment(any());

        payAdmission(invoiceId);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(incompletePublicationsOf(invoiceId)).isEqualTo(1L));
        assertThat(invoiceRow(invoiceId).get("payment_status")).isEqualTo("PAID");
        assertThat(activeEnrollmentsOf(application)).isEmpty();
        assertThat(programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId())).isEmpty();
        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);
    }

    @Test
    void anApprovalWhoseInvoiceCannotBeIssuedRollsBackTheDecisionAndLeavesTheApplicationPending() {
        Student student = persistConfirmedStudent();
        ProgramEnrollmentApplication application = requestApplication(student, persistOpenCohort(false));
        Person preceptor = persistStaff("PRECEPTOR");

        assertThatThrownBy(() -> transactionTemplate.execute(status ->
                approveUseCase.approve(new ApproveProgramEnrollmentApplicationCommand(application.getId(), preceptor))))
                .isInstanceOf(RuntimeException.class);

        assertThat(statusOf(application)).isEqualTo(ProgramEnrollmentApplicationStatus.PENDING);
        assertThat(jdbc.queryForObject("select admission_invoice_id from program_enrollment_application where id = ?", Long.class, application.getId())).isNull();
        assertThat(invoicesOf(student)).isZero();
        assertThat(activeEnrollmentsOf(application)).isEmpty();
    }

    @Test
    void rejectingAnApplicationRecordsTheReasonAndNeverIssuesAnInvoiceNorEnrolls() {
        Student student = persistConfirmedStudent();
        ProgramEnrollmentApplication application = requestApplication(student, persistOpenCohort(true));
        Person preceptor = persistStaff("PRECEPTOR");

        transactionTemplate.executeWithoutResult(status ->
                rejectUseCase.reject(new RejectProgramEnrollmentApplicationCommand(application.getId(), preceptor, "Documentación incompleta")));

        ProgramEnrollmentApplication stored = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.REJECTED);
        assertThat(stored.getRejectionReason()).isEqualTo("Documentación incompleta");
        assertThat(invoicesOf(student)).isZero();
        assertThat(activeEnrollmentsOf(application)).isEmpty();
    }

    @Test
    void aStalledEnrollmentNotifiesTheSecretariatOnlyAfterAnHourAndOnlyOnce() {
        Person staff = persistStaff("PRECEPTOR");
        ProgramEnrollmentApplication application = requestApplication(persistConfirmedStudent(), persistOpenCohort(true));
        Long invoiceId = approve(application);
        doThrow(new IllegalStateException("simulated technical failure")).when(programEnrollmentService).enrollApplicantAfterAdmissionPayment(any());
        payAdmission(invoiceId);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(incompletePublicationsOf(invoiceId)).isEqualTo(1L));

        int beforeAnHour = stalledNotifier.notifySecretariatOfStalledEnrollments();
        MutableClock.INSTANCE.advance(Duration.ofMinutes(61));
        stalledNotifier.notifySecretariatOfStalledEnrollments();
        int repeated = stalledNotifier.notifySecretariatOfStalledEnrollments();

        assertThat(beforeAnHour).isZero();
        assertThat(repeated).isZero();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(stalledNotificationsFor(staff, application)).isEqualTo(1L));
    }

    @Test
    void aRedeliveredPaidEventDoesNotDuplicateTheEnrollment() {
        ProgramEnrollmentApplication application = requestApplication(persistConfirmedStudent(), persistOpenCohort(true));
        Long invoiceId = approve(application);
        payAdmission(invoiceId);
        awaitEnrolled(application);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(AdmissionInvoicePaidEvent.paidFor(invoiceId)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(publicationsOf(invoiceId)).isZero());
        assertThat(activeEnrollmentsOf(application)).hasSize(1);
    }

    private long stalledNotificationsFor(Person staff, ProgramEnrollmentApplication application) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(staff.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.PROGRAM_ENROLLMENT_STALLED
                        && application.getId().equals(notification.getRelatedEntityId())).count();
    }
}
