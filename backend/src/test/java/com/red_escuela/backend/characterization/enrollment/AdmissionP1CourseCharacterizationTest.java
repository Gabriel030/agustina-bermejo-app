package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.usecase.paymentreceipt.ApprovePaymentReceiptUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.RejectPaymentReceiptUseCase;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentApplicationService;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.newIdempotencyKey;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.signedInAs;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.transferReceiptOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AdmissionP1CourseCharacterizationTest extends AdmissionP1CharacterizationSupport {

    @Autowired private CourseEnrollmentApplicationService applicationService;
    @Autowired private CourseEnrollmentApplicationRepository courseApplicationRepository;
    @Autowired private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired private ApprovePaymentReceiptUseCase approvePaymentReceiptUseCase;
    @Autowired private RejectPaymentReceiptUseCase rejectPaymentReceiptUseCase;

    private CourseEnrollmentApplication request(Student student, Course course) {
        return applicationService.requestWithPayment(signedInAs(student), new CreateCourseEnrollmentRequest(course.getId(), null),
                transferReceiptOf(15000), newIdempotencyKey());
    }

    private Map<String, Object> invoiceRow(Long invoiceId) {
        return jdbc.queryForMap("select total, currency, payment_status from invoice where id = ?", invoiceId);
    }

    @Test
    void requestingACourseWithAReceiptIssuesTheAdmissionInvoiceUnderReviewWithoutEnrolling() {
        Student student = persistConfirmedStudent();
        Course course = persistPricedCourse();

        CourseEnrollmentApplication application = request(student, course);

        Map<String, Object> invoice = invoiceRow(application.getInvoiceId());
        assertThat((BigDecimal) invoice.get("total")).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(invoice.get("currency")).isEqualTo("ARS");
        assertThat(invoice.get("payment_status")).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(application.getPaymentReceipt()).isNotNull();
        assertThat(application.getCourseEnrollment()).isNull();
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).isEmpty();
    }

    @Test
    void approvingTheReceiptPaysTheInvoiceAndEnrollsTheStudentThroughTheAsyncListenerLinkingApplicationAndInvoice() {
        Student student = persistConfirmedStudent();
        CourseEnrollmentApplication application = request(student, persistPricedCourse());
        Long invoiceId = application.getInvoiceId();

        approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), persistStaff("ADMINISTRATIVO"), PaymentVerification.none());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(1));
        Long enrollmentId = courseEnrollmentRepository.findByStudentId(student.getId()).get(0).getId();
        assertThat(invoiceRow(invoiceId).get("payment_status")).isEqualTo("PAID");
        assertThat(courseApplicationRepository.findByStudentIdOrderByIdDesc(student.getId()).get(0).getCourseEnrollment().getId()).isEqualTo(enrollmentId);
        assertThat(invoiceRepository.findInvoiceWithLinesById(invoiceId).orElseThrow().findCourseEnrollmentId()).contains(enrollmentId);
        List<String> roleNames = transactionTemplate.execute(status -> entityManager.find(Student.class, student.getId()).getPerson().getRoles().stream()
                .map(Role::getName).toList());
        assertThat(roleNames).contains("ALUMNO");
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId()).get(0).getStatus()).isNotEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void thePaidEventOfACourseInvoiceIsFullyConsumedAndOnlyTheCourseEnrollmentIsCreated() {
        Student student = persistConfirmedStudent();
        CourseEnrollmentApplication application = request(student, persistPricedCourse());

        approvePaymentReceiptUseCase.approve(application.getPaymentReceipt().getId(), persistStaff("ADMINISTRATIVO"), PaymentVerification.none());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(publicationsOf(application.getInvoiceId())).isZero());
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(1);
        assertThat(activeProgramEnrollmentsOf(student)).isZero();
    }

    @Test
    void rejectingTheReceiptCancelsTheInvoiceAndNeverEnrollsTheStudent() {
        Student student = persistConfirmedStudent();
        CourseEnrollmentApplication application = request(student, persistPricedCourse());

        rejectPaymentReceiptUseCase.reject(application.getPaymentReceipt().getId(), "Comprobante ilegible", persistStaff("ADMINISTRATIVO"));

        assertThat(invoiceRow(application.getInvoiceId()).get("payment_status")).isEqualTo("CANCELLED");
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).isEmpty();
        assertThat(incompletePublicationsOf(application.getInvoiceId())).isZero();
        assertThat(publicationsOf(application.getInvoiceId())).isZero();
    }

    private long activeProgramEnrollmentsOf(Student student) {
        return jdbc.queryForObject("select count(*) from program_enrollment where student_id = ?", Long.class, student.getId());
    }
}
