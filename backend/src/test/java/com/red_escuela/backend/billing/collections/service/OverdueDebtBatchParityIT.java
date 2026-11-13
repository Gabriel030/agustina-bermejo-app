package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-288")
@Tag("D-331")
@SlowTest(reason = "Boots the first Spring context of its JVM when run alone, so its time includes the one-time start-up")
class OverdueDebtBatchParityIT extends AbstractCurrentAccountIT {

    private record Scenario(String name, String paymentStatus, int dueDaysAgo, boolean programLine, Integer receiptUploadedDaysBeforeDue, boolean expectedDebt) {
    }

    @Autowired
    private StudentOverdueDebtFinder overdueDebtFinder;

    @Autowired
    private BillingConfigurationReader configurationReader;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    private Long graceVersionId;

    @AfterEach
    void removeGraceVersion() {
        if (graceVersionId != null) {
            jdbc.update("delete from configuration_version where id = ?", graceVersionId);
        }
    }

    private void publishGracePeriodOf(int days, LocalDate effectiveFrom) {
        Long staffId = inTransaction(() -> newStaffPerson().getId());
        graceVersionId = jdbc.queryForObject("""
                insert into configuration_version (scope, topic_key, config_values, status, effective_from, change_reason, proposed_by_id, proposed_at, decided_by_id, decided_at,
                                                   direct_publication)
                values ('LATE_PAYMENT', 'LATE_PAYMENT', cast(? as jsonb), 'EFFECTIVE', ?, 'Parity grace', ?, now(), ?, now(), true)
                returning id
                """, Long.class,
                "{\"type\":\"LATE_PAYMENT\",\"gracePeriodDays\":" + days + ",\"dailyLateFeeRate\":{\"fraction\":0.002},\"suspensionAfterDays\":30,"
                        + "\"suspensionAfterInvoiceCount\":2,\"blockNewEnrollmentsWhenOverdue\":true,\"lateFeeDebitNoteDueDays\":10,"
                        + "\"freezeLateFeesOnWithdrawal\":true,\"lateFeeChargingEnabled\":false}",
                Date.valueOf(effectiveFrom), staffId, staffId);
    }

    @Test
    void batchOverdueDebtMatchesTheSingleStudentDefinitionForEveryInvoiceCase() {
        LocalDate today = LocalDate.now(clock);
        publishGracePeriodOf(5, today.minusYears(1));
        int grace = configurationReader.findLatePaymentSettingsEffectiveOn(today).map(LatePaymentTerms::gracePeriodDays).orElse(0);
        assertThat(grace).isEqualTo(5);
        int pastGrace = grace + 1;
        List<Scenario> scenarios = List.of(
                new Scenario("marked overdue program", "OVERDUE", 5, true, null, true),
                new Scenario("marked overdue course", "OVERDUE", 5, false, null, true),
                new Scenario("pending past grace program", "PENDING", pastGrace, true, null, true),
                new Scenario("pending past grace course", "PENDING", pastGrace, false, null, true),
                new Scenario("pending on the last day of grace program", "PENDING", grace, true, null, false),
                new Scenario("pending on the last day of grace course", "PENDING", grace, false, null, false),
                new Scenario("pending not due program", "PENDING", -10, true, null, false),
                new Scenario("pending not due course", "PENDING", -10, false, null, false),
                new Scenario("cancelled program", "CANCELLED", 10, true, null, false),
                new Scenario("cancelled course", "CANCELLED", 10, false, null, false),
                new Scenario("paid program", "PAID", 10, true, null, false),
                new Scenario("paid course", "PAID", 10, false, null, false),
                new Scenario("under review with fresh receipt program", "PAYMENT_UNDER_REVIEW", pastGrace, true, 1, false),
                new Scenario("under review with fresh receipt course", "PAYMENT_UNDER_REVIEW", pastGrace, false, 1, false),
                new Scenario("under review with stale receipt program", "PAYMENT_UNDER_REVIEW", pastGrace, true, 40, true),
                new Scenario("under review with stale receipt course", "PAYMENT_UNDER_REVIEW", pastGrace, false, 40, true));
        Map<Long, Scenario> byStudent = new LinkedHashMap<>();
        for (Scenario scenario : scenarios) {
            byStudent.put(seed(scenario, today), scenario);
        }
        Long studentWithoutInvoices = newStudentId();

        List<Long> allStudents = new ArrayList<>(byStudent.keySet());
        allStudents.add(studentWithoutInvoices);
        Set<Long> batch = overdueDebtFinder.findStudentIdsWithOverdueDebt(allStudents, today);

        byStudent.forEach((studentId, scenario) -> {
            assertThat(batch.contains(studentId)).as(scenario.name()).isEqualTo(scenario.expectedDebt());
            assertThat(batch.contains(studentId)).as("parity " + scenario.name()).isEqualTo(overdueDebtFinder.hasOverdueDebt(studentId, today));
        });
        assertThat(batch).doesNotContain(studentWithoutInvoices);
        assertThat(overdueDebtFinder.hasOverdueDebt(studentWithoutInvoices, today)).isFalse();
    }

    private Long seed(Scenario scenario, LocalDate today) {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        LocalDate dueDate = today.minusDays(scenario.dueDaysAgo());
        Long invoiceId = issueAdministrativeFeeWithLedgerDebit(accountId, "1000.00", dueDate);
        switch (scenario.paymentStatus()) {
            case "PAID" -> jdbc.update("update invoice set payment_status = 'PAID', paid_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
            case "CANCELLED" -> jdbc.update("update invoice set payment_status = 'CANCELLED', credited_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
            default -> jdbc.update("update invoice set payment_status = ? where id = ?", scenario.paymentStatus(), invoiceId);
        }
        if (scenario.receiptUploadedDaysBeforeDue() != null) {
            jdbc.update("insert into bank_transfer_receipt (invoice_id, file_path, uploaded_at, status) values (?, 'receipts/parity.pdf', ?, 'PENDING_REVIEW')",
                    invoiceId, Timestamp.valueOf(dueDate.minusDays(scenario.receiptUploadedDaysBeforeDue()).atTime(12, 0)));
        }
        Long enrollmentId = inTransaction(() -> scenario.programLine() ? persistProgramEnrollment(studentId) : persistCourseEnrollment(studentId));
        String column = scenario.programLine() ? "program_enrollment_id" : "course_enrollment_id";
        jdbc.update("update invoice_line set " + column + " = ? where invoice_id = ?", enrollmentId, invoiceId);
        return studentId;
    }

    private Long persistProgramEnrollment(Long studentId) {
        Student student = entityManager.find(Student.class, studentId);
        Program program = new Program();
        program.setName("Parity program");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment.getId();
    }

    private Long persistCourseEnrollment(Long studentId) {
        Student student = entityManager.find(Student.class, studentId);
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("Parity " + studentId);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        Course course = new Course();
        course.setName("Parity course " + studentId);
        course.setTerm(term);
        entityManager.persist(course);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment.getId();
    }
}
