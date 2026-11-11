package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.entity.SectionGradeItemPublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectionGradeItemPublicationRepository extends JpaRepository<SectionGradeItemPublication, Long> {

    @Query("select distinct new com.red_escuela.backend.evaluation.repository.PublishedSectionRef(p.sectionKind, p.sectionId) from SectionGradeItemPublication p")
    List<PublishedSectionRef> findDistinctPublishedSections();

    List<SectionGradeItemPublication> findBySectionKindAndSectionId(SectionKind sectionKind, Long sectionId);

    boolean existsBySectionKindAndSectionIdAndGradeItemTypeAndSequenceNumber(
            SectionKind sectionKind, Long sectionId, GradeItemType gradeItemType, int sequenceNumber);
}
