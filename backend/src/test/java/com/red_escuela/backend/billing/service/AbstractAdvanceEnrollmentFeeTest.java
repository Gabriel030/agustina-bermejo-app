package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.advanceenrollmentfee.exception.AdvanceEnrollmentFeeNotAvailableException;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer.AdvanceEnrollmentFeeIssuance;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class AbstractAdvanceEnrollmentFeeTest extends AbstractInvoiceSequenceTest {

    @Autowired
    protected AdvanceEnrollmentFeeIssuer issuer;
    @Autowired
    protected EnrollmentFeeRequirementService requirementService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    protected record Setup(Career career, Year nextYear, Long enrollment, Long labeledVersion) {
    }

    protected Setup september() {
        return september(LocalDateTime.of(2027, 12, 1, 0, 0));
    }

    protected Setup september(LocalDateTime windowOpens) {
        Career career = newCareer();
        newYear(career, 2027, 10, 1, 2, null);
        Year nextYear = newYear(career, 2028, 10, 1, 2, null);
        transactionTemplate.executeWithoutResult(status -> {
            Term term = entityManager.find(Term.class, nextYear.termId());
            term.setEnrollmentStart(windowOpens);
            term.setEnrollmentEnd(LocalDateTime.of(2028, 2, 20, 23, 59));
        });
        configureYear(nextYear, template -> {
            var t = com.red_escuela.backend.billing.planning.TestTemplateTerms.termsOf(template);
            com.red_escuela.backend.billing.planning.TestTemplateTerms.replace(template, new com.red_escuela.backend.billing.configuration.domain.YearBillingTerms(
                    t.recurringFeeInvoicesCount(), t.enrollmentFeeCount(), t.firstEnrollmentFeeMonth(), t.secondEnrollmentFeeMonth(), t.enrollmentFeeLeadMonths(),
                    t.lateEnrollmentFeeDueDays(), t.recurringFeeDueDays(), t.withdrawSuspendedAtTermEnd(), t.paysWhilePaused(), t.blocksEnrollment(),
                    t.blocksExamRegistration(), t.blocksCertificates(), t.requireEnrollmentFeeForFinalExamOnlyStudents(), date(2027, 9, 1)));
        });
        Term nextTerm = transactionTemplate.execute(status -> entityManager.find(Term.class, nextYear.termId()));
        newSection(career, nextTerm);
        today(date(2027, 3, 3));
        Long enrollment = newEnrollment(career, null, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));
        Long labeled = labeledEnrollmentFeeVersion(career, 1500, 2028);
        today(date(2027, 9, 15));
        return new Setup(career, nextYear, enrollment, labeled);
    }

    protected Long labeledEnrollmentFeeVersion(Career career, long amount, int lectiveYear) {
        Long versionId = com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(career.programId(), null, FeeType.ENROLLMENT_FEE, "Matrícula",
                ars(amount), date(2028, 2, 1), career.directorId());
        jdbcTemplate.update("update price_version set applies_to_lective_year = ? where id = ?", lectiveYear, versionId);
        return versionId;
    }

    protected void actAsStaff(Career career) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(career.directorId()), "n/a", "ROLE_DIRECTOR"));
    }

    protected void actAsStudentOf(Long enrollment) {
        Long personId = jdbcTemplate.queryForObject("select s.person_id from student s join program_enrollment pe on pe.student_id = s.id where pe.id = ?", Long.class, enrollment);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(personId), "n/a", "ROLE_ALUMNO"));
    }

    protected List<Invoice> advanceInvoices(Long enrollment) {
        return invoices(enrollment, FeeType.ENROLLMENT_FEE).stream()
                .filter(invoice -> invoice.getIssuingReason() == InvoiceIssuingReason.ENROLLMENT_FEE_ADVANCE).toList();
    }
}
