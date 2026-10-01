package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProgramSubjectCorequisiteRepository extends JpaRepository<ProgramSubjectCorequisite, Long> {
    List<ProgramSubjectCorequisite> findByProgramSubjectId(Long programSubjectId);
    boolean existsByProgramSubjectIdAndCorequisiteProgramSubjectId(
            Long programSubjectId, Long corequisiteProgramSubjectId);
}
