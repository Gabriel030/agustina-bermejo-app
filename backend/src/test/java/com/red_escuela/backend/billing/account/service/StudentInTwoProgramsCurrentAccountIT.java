package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Currency;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;

class StudentInTwoProgramsCurrentAccountIT extends AbstractCurrentAccountIT {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 10);

    @PersistenceContext
    private EntityManager entityManager;

    private Long newProgramEnrollmentId(Long studentId, String programName) {
        return inTransaction(() -> {
            Program program = new Program();
            program.setName(programName + System.nanoTime());
            program.setTotalYears(3);
            program.setTotalCreditsRequired(100);
            entityManager.persist(program);
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(entityManager.getReference(Student.class, studentId));
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2027, 1, 15, 10, 0));
            entityManager.persist(enrollment);
            entityManager.flush();
            return enrollment.getId();
        });
    }

    private Long issueFeeOfProgramEnrollment(Long accountId, Long programEnrollmentId, String amount) {
        return inTransaction(() -> {
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ADMINISTRATIVE_FEE,
                    InvoiceIssuingReason.AD_HOC_CHARGE, null);
            InvoiceLineDraft line = new InvoiceLineDraft(FeeType.ADMINISTRATIVE_FEE, "Tasa", YearMonth.of(2027, 3), programEnrollmentId, null, null, null,
                    null, "TP:" + System.nanoTime(), ars(amount));
            Invoice draft = InvoiceFactory.createAdministrativeFeeInvoiceDraft(header, line, ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), DAY, DAY.plusDays(10), SNAPSHOT, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, DAY);
            return saved.getId();
        });
    }

    private Map<Long, BigDecimal> entryTotalByProgramEnrollment(Long accountId) {
        return jdbc.query(
                "select il.program_enrollment_id as program_enrollment_id, sum(e.amount) as total "
                        + "from current_account_entry e join invoice_line il on il.invoice_id = e.invoice_id "
                        + "where e.student_current_account_id = ? group by il.program_enrollment_id",
                rs -> {
                    Map<Long, BigDecimal> totals = new HashMap<>();
                    while (rs.next()) {
                        totals.put(rs.getLong("program_enrollment_id"), rs.getBigDecimal("total"));
                    }
                    return totals;
                }, accountId);
    }

    @Test
    void studentEnrolledInTwoProgramsKeepsSeparateCurrentAccountEntriesPerProgram() {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long firstProgramEnrollment = newProgramEnrollmentId(studentId, "Carrera primera ");
        Long secondProgramEnrollment = newProgramEnrollmentId(studentId, "Carrera segunda ");
        Long firstA = issueFeeOfProgramEnrollment(accountId, firstProgramEnrollment, "1000.00");
        Long firstB = issueFeeOfProgramEnrollment(accountId, firstProgramEnrollment, "500.00");
        Long second = issueFeeOfProgramEnrollment(accountId, secondProgramEnrollment, "300.00");

        StudentCurrentAccount account = loadAccount(accountId);
        List<Long> invoiceIdsOfEntries = jdbc.queryForList("select invoice_id from current_account_entry where student_current_account_id = ?", Long.class, accountId);
        Map<Long, BigDecimal> totals = entryTotalByProgramEnrollment(accountId);

        assertThat(jdbc.queryForObject("select count(*) from student_current_account where student_id = ?", Integer.class, studentId)).isEqualTo(1);
        assertThat(invoiceIdsOfEntries).containsExactlyInAnyOrder(firstA, firstB, second);
        assertThat(totals).hasSize(2);
        assertThat(totals.get(firstProgramEnrollment)).isEqualByComparingTo("1500.00");
        assertThat(totals.get(secondProgramEnrollment)).isEqualByComparingTo("300.00");
        assertThat(account.getBalance().amount()).isEqualTo(ars("1800.00"));
    }
}
