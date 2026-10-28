package com.red_escuela.backend.billing.discount.domain;

public record EnrollmentFeeDiscountFacts(boolean carriesDiscount, boolean otherLiveEnrollmentFeeInSameLectiveYear) {

    public boolean reissueWouldLoseDiscount() {
        return carriesDiscount && otherLiveEnrollmentFeeInSameLectiveYear;
    }
}
