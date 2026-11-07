package com.red_escuela.backend.evaluation.usecase.gradecorrection;

import com.red_escuela.backend.evaluation.entity.GradeItemAudit;

import java.util.List;

public interface ListGradeItemAuditUseCase {
    List<GradeItemAudit> list(Long gradeItemId);
}
