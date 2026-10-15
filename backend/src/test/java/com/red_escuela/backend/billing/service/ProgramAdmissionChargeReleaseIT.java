package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.DuplicateChargeException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.ProgramAdmissionInvoiceIssuer;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.usecase.CancelInvoiceBySystemUseCase;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-229")
@Tag("D-245")
class ProgramAdmissionChargeReleaseIT extends AbstractInvoiceSequenceTest {

    @Autowired
    private ProgramAdmissionInvoiceIssuer admissionIssuer;
    @Autowired
    private CancelInvoiceBySystemUseCase systemCancellation;

    private Long studentId;
    private Long programId;
    private Long cohortId;

    private void prepareAdmission() {
        Career career = newCareer();
        Year year = newYear(career, 2026, 10, 1, 2, null);
        cohortId = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 1, 20));
        Long enrollment = newEnrollment(career, cohortId, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 3), date(2026, 1, 20));
        studentId = transactionTemplate.execute(status -> entityManager.find(ProgramEnrollment.class, enrollment).getStudent().getId());
        programId = career.programId();
    }

    private Invoice requestAdmission() {
        return transactionTemplate.execute(status -> admissionIssuer.issueAdmissionInvoice(studentId, entityManager.find(Program.class, programId),
                entityManager.find(ProgramCohort.class, cohortId)));
    }

    @Test
    @Tag("B2-21")
    void expiredAdmissionInvoiceReleasesItsKeySoTheEnrollmentFeeCanBeRequestedAgain() {
        prepareAdmission();
        Invoice first = requestAdmission();
        String key = first.getPrimaryLine().getChargeKey();
        assertThatThrownBy(this::requestAdmission).isInstanceOf(DuplicateChargeException.class);

        systemCancellation.cancelInvoiceWithoutDirector(first.getId(), SystemCancellationOrigin.ADMISSION_EXPIRED, "La admision venció sin pago");
        Invoice second = requestAdmission();

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getPrimaryLine().getChargeKey()).isEqualTo(key);
        assertThat(invoiceRepository.findLiveInvoiceIdByChargeKey(key)).contains(second.getId());
        invariantChecker.assertAllBillingInvariantsHold();
    }
}
