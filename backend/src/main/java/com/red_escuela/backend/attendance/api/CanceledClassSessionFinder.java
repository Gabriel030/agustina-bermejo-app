package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Component
@Transactional(readOnly = true)
public class CanceledClassSessionFinder {

    private final ClassSessionRepository classSessionRepository;

    public CanceledClassSessionFinder(ClassSessionRepository classSessionRepository) {
        this.classSessionRepository = classSessionRepository;
    }

    public List<CanceledClassView> findCanceled(SectionKind kind, Collection<Long> sectionIds, LocalDate from, LocalDate to) {
        if (sectionIds.isEmpty()) {
            return List.of();
        }
        return switch (kind) {
            case PROGRAM -> classSessionRepository
                    .findBySubjectSectionIdInAndStatusAndClassDateBetween(sectionIds, ClassSessionStatus.CANCELED, from, to)
                    .stream()
                    .map(session -> new CanceledClassView(session.getSubjectSection().getId(), session.getClassDate()))
                    .toList();
            case COURSE -> classSessionRepository
                    .findByCourseSubjectSectionIdInAndStatusAndClassDateBetween(sectionIds, ClassSessionStatus.CANCELED, from, to)
                    .stream()
                    .map(session -> new CanceledClassView(session.getCourseSubjectSection().getId(), session.getClassDate()))
                    .toList();
        };
    }
}
