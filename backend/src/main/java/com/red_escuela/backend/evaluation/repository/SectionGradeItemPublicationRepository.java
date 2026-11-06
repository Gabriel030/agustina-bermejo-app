package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectionGradeItemPublicationRepository extends JpaRepository<SectionGradeItemPublication, Long> {

    List<SectionGradeItemPublication> findBySectionKindAndSectionId(SectionKind sectionKind, Long sectionId);

    boolean existsBySectionKindAndSectionIdAndGradeItemTypeAndSequenceNumber(
            SectionKind sectionKind, Long sectionId, GradeItemType gradeItemType, int sequenceNumber);
}
