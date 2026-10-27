package com.red_escuela.backend.billing.discount.domain;

public record EnrollmentFeeDiscountFacts(boolean carriesDiscount, boolean otherLiveEnrollmentFeeInSameCalendarYear) {

    public boolean reissueWouldLoseDiscount() {
        return carriesDiscount && otherLiveEnrollmentFeeInSameCalendarYear;
    }
}
