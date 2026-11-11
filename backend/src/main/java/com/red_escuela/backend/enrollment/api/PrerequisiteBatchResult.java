package com.red_escuela.backend.enrollment.api;

import java.util.List;

public record PrerequisiteBatchResult(PrerequisiteOutcome outcome, List<PrerequisiteDetail> details) {

    public boolean met() {
        return outcome != PrerequisiteOutcome.UNMET;
    }
}
