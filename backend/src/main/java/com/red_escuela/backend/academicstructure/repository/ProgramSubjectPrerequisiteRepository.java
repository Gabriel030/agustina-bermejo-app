package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramSubjectPrerequisiteRepository extends JpaRepository<ProgramSubjectPrerequisite, Long> {
    List<ProgramSubjectPrerequisite> findByProgramSubjectId(Long programSubjectId);

    @Query("""
            select p from ProgramSubjectPrerequisite p
            join fetch p.prerequisiteProgramSubject prerequisiteEntry
            join fetch prerequisiteEntry.subject
            where p.programSubject.plan.id = :planId
            """)
    List<ProgramSubjectPrerequisite> findAllOfPlan(@Param("planId") Long planId);

    List<ProgramSubjectPrerequisite> findByPrerequisiteProgramSubjectId(Long prerequisiteProgramSubjectId);

    boolean existsByProgramSubjectIdAndPrerequisiteProgramSubjectId(Long programSubjectId, Long prerequisiteProgramSubjectId);

    boolean existsByProgramSubjectIdAndPrerequisiteProgramSubjectIdAndRequirementType(
            Long programSubjectId, Long prerequisiteProgramSubjectId, RequirementType requirementType);
}
