package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.evaluation.dto.GradeItemAuditResponse;
import com.red_escuela.backend.evaluation.exception.GradeItemNotFoundException;
import com.red_escuela.backend.evaluation.repository.GradeItemAuditRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GradeItemAuditReader {

    private final GradeItemRepository gradeItemRepository;
    private final GradeItemAuditRepository auditRepository;

    public GradeItemAuditReader(GradeItemRepository gradeItemRepository, GradeItemAuditRepository auditRepository) {
        this.gradeItemRepository = gradeItemRepository;
        this.auditRepository = auditRepository;
    }

    public List<GradeItemAuditResponse> historyOf(Long gradeItemId) {
        if (!gradeItemRepository.existsById(gradeItemId)) {
            throw new GradeItemNotFoundException(gradeItemId);
        }
        return auditRepository.findByGradeItemIdOrderByChangedAtDescIdDesc(gradeItemId).stream().map(GradeItemAuditResponse::from).toList();
    }
}
