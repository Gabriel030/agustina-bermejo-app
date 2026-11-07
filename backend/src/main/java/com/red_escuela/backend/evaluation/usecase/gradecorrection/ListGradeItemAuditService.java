package com.red_escuela.backend.evaluation.usecase.gradecorrection;

import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.service.GradeItemAuditReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListGradeItemAuditService implements ListGradeItemAuditUseCase {

    private final GradeItemAuditReader auditReader;

    public ListGradeItemAuditService(GradeItemAuditReader auditReader) {
        this.auditReader = auditReader;
    }

    @Override
    public List<GradeItemAudit> list(Long gradeItemId) {
        return auditReader.historyOf(gradeItemId);
    }
}
