package com.red_escuela.backend.billing.repository;

import com.red_escuela.backend.billing.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByStatus(com.red_escuela.backend.billing.entity.PromotionStatus status);

    // "Solo la primera promoción vigente que matchea" — orderBy id asc para que, ante dos
    // promociones ACTIVE elegibles al mismo tiempo, el resultado sea determinístico.
    @Query("""
            select p from Promotion p
            where p.course.id = :courseId
              and p.status = com.red_escuela.backend.billing.entity.PromotionStatus.ACTIVE
              and :enrollmentDate between p.eligibilityValidFrom and p.eligibilityValidTo
            order by p.id asc
            """)
    List<Promotion> findActiveByCourseAndDateInWindow(@Param("courseId") Long courseId, @Param("enrollmentDate") LocalDate enrollmentDate);

    @Query("""
            select p from Promotion p
            where p.program.id = :programId
              and p.status = com.red_escuela.backend.billing.entity.PromotionStatus.ACTIVE
              and :enrollmentDate between p.eligibilityValidFrom and p.eligibilityValidTo
            order by p.id asc
            """)
    List<Promotion> findActiveByProgramAndDateInWindow(@Param("programId") Long programId, @Param("enrollmentDate") LocalDate enrollmentDate);

    default Optional<Promotion> findFirstActiveByCourseAndDateInWindow(Long courseId, LocalDate enrollmentDate) {
        return findActiveByCourseAndDateInWindow(courseId, enrollmentDate).stream().findFirst();
    }

    default Optional<Promotion> findFirstActiveByProgramAndDateInWindow(Long programId, LocalDate enrollmentDate) {
        return findActiveByProgramAndDateInWindow(programId, enrollmentDate).stream().findFirst();
    }
}
