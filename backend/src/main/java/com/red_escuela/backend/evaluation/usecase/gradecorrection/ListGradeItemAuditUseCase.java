package com.red_escuela.backend.evaluation.usecase.gradecorrection;

import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;

import java.util.List;

public interface ListGradeItemAuditUseCase {
    List<GradeItemAuditResponse> list(Long gradeItemId);
}
