package com.red_escuela.backend.characterization.enrollment;

import com.red_escuela.backend.billing.collections.exception.OverdueDebtBlocksNewEnrollmentException;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.PaymentStandingSeed;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.identity.entity.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrollmentDebtGuardCharacterizationTest extends WithdrawalP2CharacterizationSupport {

    @Autowired
    private ProgramEnrollmentService programEnrollmentService;

    private String enrollBody(Program program) {
        return "{\"programId\":" + program.getId() + "}";
    }

    private long enrollmentCountOf(Student student) {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from program_enrollment where student_id = ?", Long.class, student.getId());
    }

    private void withdrawnForNonpaymentWithOverdueDebt(Student student, int amount) {
        ProgramEnrollment oldCycle = persistProgramEnrollment(student, persistProgram(), ProgramEnrollmentStatus.WITHDRAWN);
        entityManager.flush();
        PaymentStandingSeed.seedProgramEnrollmentStanding(jdbc, oldCycle.getId(), EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        persistOverdueInstallment(oldCycle, amount);
    }

    @Test
    void studentWithoutAnyDebtEnrollsThroughHttpAndTheEnrollmentIsPersistedAsEnrolled() throws Exception {
        Student student = persistStudent();
        Program program = persistProgram();

        mockMvc.perform(post("/students/" + student.getId() + "/program-enrollments").header("Authorization", directorBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(program)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.programId").value(program.getId()));

        assertThat(enrollmentCountOf(student)).isEqualTo(1);
    }

    @Test
    void overdueDebtOfAProgramWithdrawnForNonpaymentBlocksEnrollingInAnotherProgramWithConflictAndPersistsNothing() throws Exception {
        Student student = persistStudent();
        withdrawnForNonpaymentWithOverdueDebt(student, 5000);
        long before = enrollmentCountOf(student);

        mockMvc.perform(post("/students/" + student.getId() + "/program-enrollments").header("Authorization", directorBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(persistProgram())))
                .andExpect(status().isConflict());

        assertThat(enrollmentCountOf(student)).isEqualTo(before);
    }

    @Test
    void theWithdrawnForNonpaymentDebtBlockRaisesOutstandingDebtBlocksReenrollment() {
        Student student = persistStudent();
        withdrawnForNonpaymentWithOverdueDebt(student, 5000);

        assertThatThrownBy(() -> programEnrollmentService.verifyStudentHasNoOutstandingDebt(student.getId()))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
    }

    @Test
    void overdueInstallmentOfAnActiveEnrollmentBlocksANewEnrollmentWithOverdueDebtException() {
        Student student = persistStudent();
        ProgramEnrollment active = persistProgramEnrollment(student, persistProgram(), ProgramEnrollmentStatus.ENROLLED);
        persistOverdueInstallment(active, 5000);

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(persistProgram().getId())))
                .isInstanceOf(OverdueDebtBlocksNewEnrollmentException.class);
    }

    @Test
    void pendingInstallmentNotYetDueDoesNotBlockANewEnrollment() {
        Student student = persistStudent();
        ProgramEnrollment active = persistProgramEnrollment(student, persistProgram(), ProgramEnrollmentStatus.ENROLLED);
        persistPendingInstallment(active, YearMonth.now(), LocalDate.now().plusDays(10), 5000);

        ProgramEnrollment created = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(persistProgram().getId()));

        assertThat(created.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void payingTheOverdueInstallmentInFullLiftsTheBlock() {
        Student student = persistStudent();
        ProgramEnrollment active = persistProgramEnrollment(student, persistProgram(), ProgramEnrollmentStatus.ENROLLED);
        Invoice overdue = persistOverdueInstallment(active, 5000);
        BillingTestFixtures.payInvoiceInFull(overdue);

        ProgramEnrollment created = programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(persistProgram().getId()));

        assertThat(created.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }

    @Test
    void enrollAfterAdmissionPaymentSkipsTheDebtGuardWhileEnrollDoesNot() {
        Student student = persistStudent();
        withdrawnForNonpaymentWithOverdueDebt(student, 5000);
        Program program = persistProgram();

        assertThatThrownBy(() -> programEnrollmentService.enroll(student.getId(), new CreateProgramEnrollmentRequest(program.getId())))
                .isInstanceOf(OutstandingDebtBlocksReenrollmentException.class);
        ProgramEnrollment created = programEnrollmentService.enrollAfterAdmissionPayment(student.getId(), new CreateProgramEnrollmentRequest(program.getId()));

        assertThat(created.getStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED);
    }
}
