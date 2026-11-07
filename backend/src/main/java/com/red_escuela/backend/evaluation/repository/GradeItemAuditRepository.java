package com.red_escuela.backend.evaluation.repository;

import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItemAuditKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface GradeItemAuditRepository extends JpaRepository<GradeItemAudit, Long> {

    List<GradeItemAudit> findBySectionKindAndSectionIdAndKind(SectionKind sectionKind, Long sectionId, GradeItemAuditKind kind);

    List<GradeItemAudit> findByGradeItemIdInAndKindOrderByChangedAtDescIdDesc(Collection<Long> gradeItemIds, GradeItemAuditKind kind);

    List<GradeItemAudit> findByGradeItemIdOrderByChangedAtDescIdDesc(Long gradeItemId);
}
