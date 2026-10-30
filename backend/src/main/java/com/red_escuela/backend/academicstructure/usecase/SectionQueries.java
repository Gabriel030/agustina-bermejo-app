package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseSectionView;
import com.red_escuela.backend.academicstructure.dto.ProgramSectionView;

import java.util.Optional;

public interface SectionQueries {

    ProgramSectionView findProgramSection(Long id);

    CourseSectionView findCourseSection(Long id);

    boolean exists(SectionKind kind, Long id);

    void requireExists(SectionKind kind, Long id);

    void lockProgramSection(Long id);

    void lockCourseSection(Long id);

    Optional<Long> findEvaluationSchemeId(SectionKind kind, Long id);

    boolean isEvaluationSchemeAssigned(Long schemeId);
}
