package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import jakarta.persistence.EntityManager;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

@Component
public class LegacyFixturePlanFiller implements HibernatePropertiesCustomizer {

    private static final String PLAN_PROPERTY = "plan";

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.session_factory.interceptor", new PlanFillingInterceptor());
    }

    static final class PlanFillingInterceptor implements Interceptor {

        @Override
        public boolean onPersist(Object entity, Object id, Object[] state, String[] propertyNames, Type[] propertyTypes) {
            Program program = programOf(entity);
            if (program == null) {
                return false;
            }
            int planIndex = Arrays.asList(propertyNames).indexOf(PLAN_PROPERTY);
            if (planIndex < 0 || state[planIndex] != null) {
                return false;
            }
            state[planIndex] = TestCurriculumPlans.planWithoutFlushOf(program);
            return true;
        }

        private Program programOf(Object entity) {
            if (entity instanceof ProgramEnrollment enrollment && enrollment.getPlan() == null) {
                return enrollment.getProgram();
            }
            if (entity instanceof ProgramCohort cohort && cohort.getPlan() == null && cohort.getProgramId() != null) {
                EntityManager entityManager = TestCurriculumPlans.currentEntityManagerOrNull();
                return entityManager == null ? null : entityManager.getReference(Program.class, cohort.getProgramId());
            }
            return null;
        }
    }
}
