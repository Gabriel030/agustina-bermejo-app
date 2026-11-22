package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProgramSubjectCorequisiteRepository extends JpaRepository<ProgramSubjectCorequisite, Long> {
    List<ProgramSubjectCorequisite> findByProgramSubjectId(Long programSubjectId);

    @Query("""
            select c from ProgramSubjectCorequisite c
            join fetch c.corequisiteProgramSubject corequisiteEntry
            join fetch corequisiteEntry.subject
            where c.programSubject.program.id = :programId
            """)
    List<ProgramSubjectCorequisite> findAllOfProgram(@Param("programId") Long programId);

    boolean existsByProgramSubjectIdAndCorequisiteProgramSubjectId(
            Long programSubjectId, Long corequisiteProgramSubjectId);
}
