package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.RequirementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramSubjectPrerequisiteRepository extends JpaRepository<ProgramSubjectPrerequisite, Long> {
    List<ProgramSubjectPrerequisite> findByProgramSubjectId(Long programSubjectId);

    List<ProgramSubjectPrerequisite> findByPrerequisiteProgramSubjectId(Long prerequisiteProgramSubjectId);

    boolean existsByProgramSubjectIdAndPrerequisiteProgramSubjectIdAndRequirementType(
            Long programSubjectId, Long prerequisiteProgramSubjectId, RequirementType requirementType);
}
