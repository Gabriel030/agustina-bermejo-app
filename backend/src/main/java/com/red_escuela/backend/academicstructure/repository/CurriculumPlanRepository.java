package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurriculumPlanRepository extends JpaRepository<CurriculumPlan, Long> {

    List<CurriculumPlan> findByProgramIdOrderByVersionNumberAsc(Long programId);

    @Query("""
            select coalesce(max(cp.versionNumber), 0) from CurriculumPlan cp where cp.program.id = :programId
            """)
    int findLastVersionNumber(@Param("programId") Long programId);

    @Query("""
            select cp from CurriculumPlan cp
            where cp.program.id = :programId
            and cp.status <> com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus.DRAFT
            order by cp.versionNumber desc
            limit 1
            """)
    Optional<CurriculumPlan> findCurrentByProgramId(@Param("programId") Long programId);
}
