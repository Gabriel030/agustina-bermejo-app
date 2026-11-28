package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ChangeProgramEnrollmentPlanRequest(@NotNull Long targetPlanId,
                                                 @NotNull @Valid List<RecordSubjectEquivalenceRequest> equivalences) {
}
