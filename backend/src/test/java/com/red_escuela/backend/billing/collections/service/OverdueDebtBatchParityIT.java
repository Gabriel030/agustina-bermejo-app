package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.account.service.AbstractCurrentAccountIT;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-264")
@SlowTest(reason = "Boots the first Spring context of its JVM when run alone, so its time includes the one-time start-up")
class OverdueDebtBatchParityIT extends AbstractCurrentAccountIT {

    private record Scenario(String paymentStatus, int dueOffsetDays, boolean programLine, boolean expectedDebt) {
    }

    @Autowired
    private InvoiceQueryService invoiceQueryService;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    @Test
    void batchOverdueDebtMatchesTheSingleStudentQueryForEveryInvoiceCase() {
        LocalDate today = LocalDate.now(clock);
        List<Scenario> scenarios = List.of(
                new Scenario("OVERDUE", -5, true, true),
                new Scenario("OVERDUE", -5, false, true),
                new Scenario("PENDING", -1, true, true),
                new Scenario("PENDING", -1, false, true),
                new Scenario("PENDING", 10, true, false),
                new Scenario("PENDING", 10, false, false),
                new Scenario("CANCELLED", -10, true, false),
                new Scenario("CANCELLED", -10, false, false),
                new Scenario("PAID", -10, true, false),
                new Scenario("PAID", -10, false, false));
        Map<Long, Scenario> byStudent = new LinkedHashMap<>();
        for (Scenario scenario : scenarios) {
            byStudent.put(seed(scenario, today), scenario);
        }
        Long studentWithoutInvoices = newStudentId();

        List<Long> allStudents = new java.util.ArrayList<>(byStudent.keySet());
        allStudents.add(studentWithoutInvoices);
        Set<Long> batch = invoiceQueryService.findStudentIdsWithOverdueDebt(allStudents, today);

        byStudent.forEach((studentId, scenario) -> {
            assertThat(batch.contains(studentId)).as(scenario.toString()).isEqualTo(scenario.expectedDebt());
            assertThat(batch.contains(studentId)).as("parity " + scenario).isEqualTo(invoiceQueryService.hasOverdueDebt(studentId));
        });
        assertThat(batch).doesNotContain(studentWithoutInvoices);
        assertThat(invoiceQueryService.hasOverdueDebt(studentWithoutInvoices)).isFalse();
    }

    private Long seed(Scenario scenario, LocalDate today) {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long invoiceId = issueAdministrativeFeeWithLedgerDebit(accountId, "1000.00", today.plusDays(scenario.dueOffsetDays()));
        switch (scenario.paymentStatus()) {
            case "PAID" -> jdbc.update("update invoice set payment_status = 'PAID', paid_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
            case "CANCELLED" -> jdbc.update("update invoice set payment_status = 'CANCELLED', credited_amount = total, outstanding_amount = 0 where id = ?", invoiceId);
            default -> jdbc.update("update invoice set payment_status = ? where id = ?", scenario.paymentStatus(), invoiceId);
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
