package com.red_escuela.backend.billing.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

class ProgramEnrollmentBillingSetupTest {

    private final ProgramEnrollmentRepository enrollmentRepository = mock(ProgramEnrollmentRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final ProgramEnrollmentBillingSetup setup = new ProgramEnrollmentBillingSetup(enrollmentRepository, mock(InvoiceRepository.class), entityManager);

    @Test
    void cohortWithoutTemplateLeavesTheEnrollmentWithoutBillingTemplate() {
        ProgramCohort cohort = mock(ProgramCohort.class);
        when(cohort.getTemplateId()).thenReturn(null);
        ProgramEnrollment enrollment = mock(ProgramEnrollment.class);
        when(enrollment.getCurrentBillingTemplate()).thenReturn(null);
        when(enrollment.getProgramCohort()).thenReturn(cohort);

        setup.assignTemplateIfMissing(enrollment);

        verifyNoInteractions(entityManager, enrollmentRepository);
    }
}
