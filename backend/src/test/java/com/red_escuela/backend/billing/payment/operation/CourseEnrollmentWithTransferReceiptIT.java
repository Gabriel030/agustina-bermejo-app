package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseEnrollmentWithTransferReceiptIT extends AbstractBankTransferReceiptHttpIT {

    private Course persistPricedCourse() {
        return inTransaction(() -> {
            Term term = new Term();
            term.setYear(2027);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(20);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba", ars("15000.00"), TODAY.minusDays(30),
                    newStaffPerson().getId());
            return course;
        });
    }

    private MockMultipartHttpServletRequestBuilder enrollment(Debtor student, Course course, boolean withTransferDetails) {
        return enrollment(student, course, withTransferDetails, "15000.00", List.of());
    }

    private MockMultipartHttpServletRequestBuilder enrollment(Debtor student, Course course, boolean withTransferDetails, String amount, List<Long> invoiceIds) {
        MockMultipartHttpServletRequestBuilder builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/me/course-enrollments");
        builder.param("courseId", course.getId().toString());
        builder.header("Authorization", "Bearer " + studentTokenOf(student));
        builder.header("Idempotency-Key", nextKey());
        if (!invoiceIds.isEmpty()) {
            builder.param("invoiceIds", invoiceIds.stream().map(String::valueOf).toArray(String[]::new));
        }
        if (withTransferDetails) {
            builder.file(pdfFile("curso-" + System.nanoTime()));
            builder.param("declaredAmount", amount);
            builder.param("currency", "ARS");
            builder.param("transferDate", "2027-03-09");
            builder.param("operationNumber", nextOperationNumber());
            builder.param("originAccount", "0170099220000067797370");
            builder.param("holderName", "Ana Pérez");
        } else {
            builder.file(new org.springframework.mock.web.MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", pdfBytes("viejo")));
        }
        return builder;
    }

    private Long admissionInvoiceOf(JsonNode application) {
        return jdbc.queryForObject("select invoice_id from course_enrollment_application where id = ?", Long.class, application.get("id").asLong());
    }

    @Test
    @Tag("D-471")
    @Tag("D-267")
    @Tag("D-260")
    void courseRequestCreatesPendingPaymentAndProtectsTheAdmissionInvoice() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor student = debtor();
        Course course = persistPricedCourse();

        MvcResult result = mockMvc.perform(enrollment(student, course, true)).andExpect(status().isCreated()).andReturn();

        JsonNode body = bodyOf(result);
        assertThat(body.get("paymentStatus").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(body.get("courseEnrollmentId").isNull()).isTrue();
        Long invoiceId = admissionInvoiceOf(body);
        Long paymentId = jdbc.queryForObject("select payment_id from bank_transfer_receipt where invoice_id = ?", Long.class, invoiceId);
        assertThat(paymentId).isNotNull();
        assertThat(jdbc.queryForObject("select status from payment where id = ?", String.class, paymentId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForList("select invoice_id from payment_checkout_item where payment_id = ?", Long.class, paymentId)).containsExactly(invoiceId);
        assertThat(paymentStatusOfInvoice(invoiceId)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(filesStoredFor(student.studentId())).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-471")
    void courseRequestWithoutTransferDetailsReturns400() throws Exception {
        Debtor student = debtor();
        Course course = persistPricedCourse();

        MvcResult result = mockMvc.perform(enrollment(student, course, false)).andExpect(status().isBadRequest()).andReturn();

        assertThat(bodyOf(result).get("code").asText()).isEqualTo("BANK_TRANSFER_DETAILS_REQUIRED");
        assertThat(jdbc.queryForObject("select count(*) from course_enrollment_application where student_id = ?", Long.class, student.studentId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from invoice where student_current_account_id = ?", Long.class, student.accountId())).isZero();
        assertThat(filesStoredFor(student.studentId())).isZero();
    }

    @Test
    @Tag("D-471")
    @Tag("D-153")
    void courseRequestWithAnOlderUnpaidInvoiceNotIncludedReturns409() throws Exception {
        Debtor student = debtor();
        invoiceOf(student, "80000.00", TODAY.minusDays(20));
        Course course = persistPricedCourse();

        MvcResult result = mockMvc.perform(enrollment(student, course, true)).andExpect(status().isConflict()).andReturn();

        assertThat(bodyOf(result).get("code").asText()).isEqualTo("RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST");
        assertThat(jdbc.queryForObject("select count(*) from course_enrollment_application where student_id = ?", Long.class, student.studentId())).isZero();
        assertThat(paymentsOf(student.accountId())).isZero();
    }

    @Test
    @Tag("D-471")
    @Tag("D-153")
    void courseRequestIncludingTheOlderInvoiceCoversBothInOrder() throws Exception {
        Debtor student = debtor();
        Long older = invoiceOf(student, "80000.00", TODAY.minusDays(20));
        Course course = persistPricedCourse();

        MvcResult result = mockMvc.perform(enrollment(student, course, true, "95000.00", List.of(older))).andExpect(status().isCreated()).andReturn();

        Long invoiceId = admissionInvoiceOf(bodyOf(result));
        Long paymentId = jdbc.queryForObject("select payment_id from bank_transfer_receipt where invoice_id = ?", Long.class, older);
        assertThat(jdbc.queryForList("select invoice_id from payment_checkout_item where payment_id = ? order by position", Long.class, paymentId))
                .isEqualTo(List.of(older, invoiceId));
    }
}
