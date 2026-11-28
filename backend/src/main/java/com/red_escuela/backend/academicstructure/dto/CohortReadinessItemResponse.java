package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.CohortReadinessItemCode;

import java.util.List;

public record CohortReadinessItemResponse(CohortReadinessItemCode code, boolean blocking, boolean fulfilled, List<String> reasons) {
}
