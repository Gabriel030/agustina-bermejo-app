package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import jakarta.persistence.EntityManager;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;

public final class TestCurriculumPlans {

    private static final LocalDate PLAN_START = LocalDate.of(2020, 1, 1);

    private TestCurriculumPlans() {
    }

    public static CurriculumPlan planOf(Program program) {
        EntityManager entityManager = currentEntityManager();
        if (entityManager == null || program == null || program.getId() == null) {
            return CurriculumPlan.initialVersionOf(program, PLAN_START);
        }
        return entityManager.createQuery("""
                        select cp from CurriculumPlan cp
                        where cp.program.id = :programId and cp.status <> :draft
                        order by cp.versionNumber desc
                        """, CurriculumPlan.class)
                .setParameter("programId", program.getId())
                .setParameter("draft", CurriculumPlanStatus.DRAFT)
                .setMaxResults(1)
                .getResultStream().findFirst()
                .orElseGet(() -> {
                    CurriculumPlan plan = CurriculumPlan.initialVersionOf(program, PLAN_START);
                    entityManager.persist(plan);
                    return plan;
                });
    }

    public static EntityManager currentEntityManagerOrNull() {
        return currentEntityManager();
    }

    public static CurriculumPlan planWithoutFlushOf(Program program) {
        EntityManager entityManager = currentEntityManager();
        return entityManager.createQuery("""
                        select cp from CurriculumPlan cp
                        where cp.program.id = :programId and cp.status <> :draft
                        order by cp.versionNumber desc
                        """, CurriculumPlan.class)
                .setFlushMode(jakarta.persistence.FlushModeType.COMMIT)
                .setParameter("programId", program.getId())
                .setParameter("draft", CurriculumPlanStatus.DRAFT)
                .setMaxResults(1)
                .getResultStream().findFirst()
                .orElseGet(() -> {
                    CurriculumPlan plan = CurriculumPlan.initialVersionOf(program, PLAN_START);
                    entityManager.persist(plan);
                    return plan;
                });
    }

    private static EntityManager currentEntityManager() {
        return TransactionSynchronizationManager.getResourceMap().values().stream()
                .filter(EntityManagerHolder.class::isInstance)
                .map(holder -> ((EntityManagerHolder) holder).getEntityManager())
                .findFirst().orElse(null);
    }
}
