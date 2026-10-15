package com.red_escuela.backend.billing.configuration.domain;

public record YearBillingTerms(int recurringFeeInvoicesCount, int enrollmentFeeCount, int firstEnrollmentFeeMonth, Integer secondEnrollmentFeeMonth,
                               int enrollmentFeeLeadMonths, int lateEnrollmentFeeDueDays, int recurringFeeDueDays, boolean withdrawSuspendedAtTermEnd,
                               boolean paysWhilePaused, boolean blocksEnrollment, boolean blocksExamRegistration, boolean blocksCertificates,
                               boolean requireEnrollmentFeeForFinalExamOnlyStudents) implements ConfigurationValues {

    public YearBillingTerms {
        if (recurringFeeInvoicesCount < 0) {
            throw new InvalidConfigurationValuesException("La cantidad de cuotas no puede ser negativa");
        }
        if (enrollmentFeeCount != 1 && enrollmentFeeCount != 2) {
            throw new InvalidConfigurationValuesException("La cantidad de matrículas debe ser 1 o 2");
        }
        if (firstEnrollmentFeeMonth < 1 || firstEnrollmentFeeMonth > 12) {
            throw new InvalidConfigurationValuesException("El mes de matrícula debe estar entre 1 y 12");
        }
        if (enrollmentFeeCount == 1 && secondEnrollmentFeeMonth != null) {
            throw new InvalidConfigurationValuesException("Con 1 matrícula no hay mes de segunda matrícula");
        }
        if (enrollmentFeeCount == 2 && (secondEnrollmentFeeMonth == null || secondEnrollmentFeeMonth <= firstEnrollmentFeeMonth || secondEnrollmentFeeMonth > 12)) {
            throw new InvalidConfigurationValuesException("Con 2 matrículas, el mes de la segunda debe ser posterior al de la primera");
        }
        if (enrollmentFeeLeadMonths < 0 || lateEnrollmentFeeDueDays < 0 || recurringFeeDueDays < 0) {
            throw new InvalidConfigurationValuesException("Los plazos no pueden ser negativos");
        }
    }

    public static YearBillingTerms initial(int recurringFeeInvoicesCount) {
        return new YearBillingTerms(recurringFeeInvoicesCount, 1, 2, null, 1, 10, 10, true, false, true, true, true, false);
    }

    public int remainingInstallments(long installmentsGeneratedInWindow) {
        return recurringFeeInvoicesCount - (int) installmentsGeneratedInWindow;
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.YEAR_TERMS;
    }
}
