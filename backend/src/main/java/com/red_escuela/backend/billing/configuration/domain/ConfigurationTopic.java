package com.red_escuela.backend.billing.configuration.domain;

import java.util.Objects;

public record ConfigurationTopic(ConfigurationScope scope, Long programId, Long termId, Long courseId, Long cohortId) {

    public ConfigurationTopic {
        Objects.requireNonNull(scope, "scope");
        boolean valid = switch (scope) {
            case YEAR_TERMS, ANNUAL_UPFRONT -> programId != null && termId != null && courseId == null && cohortId == null;
            case COHORT_ENTRY -> cohortId != null && programId == null && termId == null && courseId == null;
            case COURSE_TERMS -> courseId != null && programId == null && termId == null && cohortId == null;
            case LATE_PAYMENT, ISSUANCE, ALLOCATION, IRREGULAR_TERMS -> programId == null && termId == null && courseId == null && cohortId == null;
        };
        if (!valid) {
            throw new InvalidConfigurationValuesException("El tema no corresponde al ámbito " + scope);
        }
    }

    public static ConfigurationTopic ofYearTerms(Long programId, Long termId) {
        return new ConfigurationTopic(ConfigurationScope.YEAR_TERMS, programId, termId, null, null);
    }

    public static ConfigurationTopic ofAnnualUpfront(Long programId, Long termId) {
        return new ConfigurationTopic(ConfigurationScope.ANNUAL_UPFRONT, programId, termId, null, null);
    }

    public static ConfigurationTopic ofCohortEntry(Long cohortId) {
        return new ConfigurationTopic(ConfigurationScope.COHORT_ENTRY, null, null, null, cohortId);
    }

    public static ConfigurationTopic ofCourseTerms(Long courseId) {
        return new ConfigurationTopic(ConfigurationScope.COURSE_TERMS, null, null, courseId, null);
    }

    public static ConfigurationTopic ofGlobal(ConfigurationScope scope) {
        return new ConfigurationTopic(scope, null, null, null, null);
    }

    public String key() {
        return switch (scope) {
            case YEAR_TERMS, ANNUAL_UPFRONT -> scope + ":P" + programId + ":T" + termId;
            case COHORT_ENTRY -> scope + ":C" + cohortId;
            case COURSE_TERMS -> scope + ":C" + courseId;
            case LATE_PAYMENT, ISSUANCE, ALLOCATION, IRREGULAR_TERMS -> scope.name();
        };
    }
}
