package com.red_escuela.backend.billing.configuration.domain;

public record CourseTerms(int monthlyInstallmentDueDays) implements ConfigurationValues {

    public CourseTerms {
        if (monthlyInstallmentDueDays < 0) {
            throw new InvalidConfigurationValuesException("El vencimiento de la cuota de curso no puede ser negativo");
        }
    }

    public static CourseTerms initial() {
        return new CourseTerms(10);
    }

    @Override
    public ConfigurationScope scope() {
        return ConfigurationScope.COURSE_TERMS;
    }
}
