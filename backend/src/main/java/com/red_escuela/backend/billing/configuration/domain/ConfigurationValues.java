package com.red_escuela.backend.billing.configuration.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = YearBillingTerms.class, name = "YEAR_TERMS"),
        @JsonSubTypes.Type(value = CohortEntryDiscount.class, name = "COHORT_ENTRY"),
        @JsonSubTypes.Type(value = LatePaymentTerms.class, name = "LATE_PAYMENT"),
        @JsonSubTypes.Type(value = IssuanceSettings.class, name = "ISSUANCE"),
        @JsonSubTypes.Type(value = AllocationSettings.class, name = "ALLOCATION"),
        @JsonSubTypes.Type(value = AnnualUpfrontSettings.class, name = "ANNUAL_UPFRONT"),
        @JsonSubTypes.Type(value = CourseTerms.class, name = "COURSE_TERMS"),
        @JsonSubTypes.Type(value = IrregularTermTypes.class, name = "IRREGULAR_TERMS"),
        @JsonSubTypes.Type(value = BillingDashboardGoals.class, name = "BILLING_DASHBOARD_GOALS")
})
public sealed interface ConfigurationValues
        permits YearBillingTerms, CohortEntryDiscount, LatePaymentTerms, IssuanceSettings, AllocationSettings, AnnualUpfrontSettings, CourseTerms, IrregularTermTypes,
        BillingDashboardGoals {

    ConfigurationScope scope();
}
