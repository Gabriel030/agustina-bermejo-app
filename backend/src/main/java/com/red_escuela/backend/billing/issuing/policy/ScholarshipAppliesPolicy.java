package com.red_escuela.backend.billing.issuing.policy;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "SCHOLARSHIP_NOT_COVERING_FEE_TYPE", decisions = {"D-157"})
public final class ScholarshipAppliesPolicy implements Policy<ScholarshipAppliesPolicy.Candidate> {
    public record Candidate(Scholarship scholarship, FeeType feeType, boolean programCharge) {
    }

    public String name() {
        return "ScholarshipAppliesPolicy";
    }

    public PolicyVerdict evaluate(Candidate candidate) {
        boolean covered = !candidate.programCharge() || candidate.scholarship().getAppliesTo().covers(candidate.feeType());
        return covered ? PolicyVerdict.allow(name()) : PolicyVerdict.deny(name(), "SCHOLARSHIP_NOT_COVERING_FEE_TYPE", "La beca no cubre este tipo de cargo");
    }
}
