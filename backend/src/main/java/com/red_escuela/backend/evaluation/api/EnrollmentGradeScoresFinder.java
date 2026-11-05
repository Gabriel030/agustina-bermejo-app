package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
@Transactional(readOnly = true)
public class EnrollmentGradeScoresFinder {

    private final GradeItemRepository gradeItemRepository;

    public EnrollmentGradeScoresFinder(GradeItemRepository gradeItemRepository) {
        this.gradeItemRepository = gradeItemRepository;
    }

    public Map<Long, EnrollmentGradeScores> findScoresByProgramEnrollment(List<Long> enrollmentIds) {
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        return scoresByEnrollment(gradeItemRepository.findByProgramSubjectSectionEnrollmentIdIn(enrollmentIds),
                item -> item.getProgramSubjectSectionEnrollment().getId());
    }

    public Map<Long, EnrollmentGradeScores> findScoresByCourseEnrollment(List<Long> enrollmentIds) {
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        return scoresByEnrollment(gradeItemRepository.findByCourseSubjectSectionEnrollmentIdIn(enrollmentIds),
                item -> item.getCourseSubjectSectionEnrollment().getId());
    }

    private Map<Long, EnrollmentGradeScores> scoresByEnrollment(List<GradeItem> items,
                                                                Function<GradeItem, Long> enrollmentIdOf) {
        Map<Long, List<GradeItem>> byEnrollment = new HashMap<>();
        for (GradeItem item : items) {
            byEnrollment.computeIfAbsent(enrollmentIdOf.apply(item), key -> new ArrayList<>()).add(item);
        }
        Map<Long, EnrollmentGradeScores> scores = new HashMap<>();
        byEnrollment.forEach((enrollmentId, enrollmentItems) -> scores.put(enrollmentId, new EnrollmentGradeScores(
                scoresOf(enrollmentItems, GradeItemType.EXAM),
                scoresOf(enrollmentItems, GradeItemType.TP),
                scoresOf(enrollmentItems, GradeItemType.FINAL))));
        return scores;
    }

    private Map<Integer, Double> scoresOf(List<GradeItem> items, GradeItemType type) {
        Map<Integer, Double> scores = new HashMap<>();
        for (GradeItem item : items) {
            if (item.getType() == type && item.getSequenceNumber() != null && item.getScore() != null) {
                scores.put(item.getSequenceNumber(), item.getScore().value().doubleValue());
            }
        }
        return scores;
    }
}
