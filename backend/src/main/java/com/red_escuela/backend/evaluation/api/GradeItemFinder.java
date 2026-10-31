package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class GradeItemFinder {

    private final GradeItemRepository gradeItemRepository;

    public GradeItemFinder(GradeItemRepository gradeItemRepository) {
        this.gradeItemRepository = gradeItemRepository;
    }

    public List<GradeItemView> findByProgramEnrollment(Long enrollmentId) {
        return gradeItemRepository.findByProgramSubjectSectionEnrollmentId(enrollmentId).stream()
                .map(GradeItemFinder::viewOf)
                .toList();
    }

    private static GradeItemView viewOf(GradeItem gradeItem) {
        Long enrollmentId = gradeItem.getProgramSubjectSectionEnrollment() != null
                ? gradeItem.getProgramSubjectSectionEnrollment().getId()
                : gradeItem.getCourseSubjectSectionEnrollment().getId();
        return new GradeItemView(
                gradeItem.getId(),
                enrollmentId,
                gradeItem.getType().name(),
                gradeItem.getSequenceNumber(),
                gradeItem.getScore(),
                gradeItem.getDate(),
                gradeItem.getExamBoard() == null ? null : gradeItem.getExamBoard().getId());
    }
}
