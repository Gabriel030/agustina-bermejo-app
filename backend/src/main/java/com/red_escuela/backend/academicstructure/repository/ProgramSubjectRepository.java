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

    String CURRENT_PLAN_ID_OF_PROGRAM = """
            (select cp.id from CurriculumPlan cp
             where cp.program.id = :programId
             and cp.status <> com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus.DRAFT
             and cp.versionNumber = (select max(latest.versionNumber) from CurriculumPlan latest
                                     where latest.program.id = :programId
                                     and latest.status <> com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus.DRAFT))
            """;

    @EntityGraph(attributePaths = "subject")
    @Query("select ps from ProgramSubject ps where ps.plan.id = " + CURRENT_PLAN_ID_OF_PROGRAM + " order by ps.id")
    List<ProgramSubject> findByProgramId(@Param("programId") Long programId);

    @EntityGraph(attributePaths = "subject")
    @Query("""
            select ps from ProgramSubject ps
            where ps.plan.id = """ + CURRENT_PLAN_ID_OF_PROGRAM + """
            and (ps.status is null or ps.status = com.red_escuela.backend.shared.domain.StatusType.ACTIVE)
            order by ps.id
            """)
    List<ProgramSubject> findActiveByProgramId(@Param("programId") Long programId);

    @Query("select ps from ProgramSubject ps where ps.status = :status and ps.plan.id = " + CURRENT_PLAN_ID_OF_PROGRAM + " order by ps.id")
    List<ProgramSubject> findByProgramIdAndStatus(@Param("programId") Long programId, @Param("status") StatusType status);

    @EntityGraph(attributePaths = "subject")
    @Query("""
            select ps from ProgramSubject ps
            where ps.plan.id = :planId
            and (ps.status is null or ps.status = com.red_escuela.backend.shared.domain.StatusType.ACTIVE)
            order by ps.id
            """)
    List<ProgramSubject> findActiveByPlanId(@Param("planId") Long planId);

    @Query("select ps from ProgramSubject ps where ps.subject.id = :subjectId and ps.plan.id = " + CURRENT_PLAN_ID_OF_PROGRAM)
    Optional<ProgramSubject> findByProgramIdAndSubjectId(@Param("programId") Long programId, @Param("subjectId") Long subjectId);

    @EntityGraph(attributePaths = "subject")
    List<ProgramSubject> findByPlanIdOrderById(Long planId);

    List<ProgramSubject> findByPlanIdAndStatusOrderById(Long planId, StatusType status);

    Optional<ProgramSubject> findByPlanIdAndSubjectId(Long planId, Long subjectId);

    List<ProgramSubject> findByProgramIdAndElectiveGroupId(Long programId, Long electiveGroupId);
}
