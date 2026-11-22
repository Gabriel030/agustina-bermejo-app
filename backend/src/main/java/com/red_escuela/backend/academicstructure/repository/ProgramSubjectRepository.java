package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramSubjectRepository extends JpaRepository<ProgramSubject, Long> {

    @EntityGraph(attributePaths = "subject")
    List<ProgramSubject> findByProgramId(Long programId);

    @EntityGraph(attributePaths = "subject")
    @Query("""
            select ps from ProgramSubject ps
            where ps.program.id = :programId
            and (ps.status is null or ps.status = com.red_escuela.backend.shared.domain.StatusType.ACTIVE)
            order by ps.id
            """)
    List<ProgramSubject> findActiveByProgramId(@Param("programId") Long programId);

    List<ProgramSubject> findByProgramIdAndStatus(Long programId, StatusType status);

    Optional<ProgramSubject> findByProgramIdAndSubjectId(Long programId, Long subjectId);

    List<ProgramSubject> findByProgramIdAndElectiveGroupId(Long programId, Long electiveGroupId);
}
