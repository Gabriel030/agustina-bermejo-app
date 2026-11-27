package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.event.service.ProcessedEffectGuard;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrityGuardsReproductionIT extends ReproductionFixture {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ProcessedEffectGuard processedEffectGuard;

    private List<String> checkDefinitionsOf(String table) {
        return jdbcTemplate.queryForList(
                "select pg_get_constraintdef(c.oid) from pg_constraint c where c.contype = 'c' and c.conrelid = ?::regclass",
                String.class, table);
    }

    @Test
    @Disabled("E34-B-P8")
    void sectionCapacityMustBePositiveInSql() {
        for (String table : List.of("subject_section", "course_section", "course", "classroom")) {
            assertThat(checkDefinitionsOf(table)).as(table).anyMatch(definition -> definition.contains("capacity"));
        }
    }

    @Test
    @Disabled("E34-B-P8")
    void attemptNumberIsAtLeastOneInSqlForBothEnrollmentKinds() {
        for (String table : List.of("subject_section_enrollment", "course_section_enrollment")) {
            assertThat(checkDefinitionsOf(table)).as(table).anyMatch(definition -> definition.contains("attempt_number"));
        }
    }

    @Test
    @Disabled("E34-B-P8")
    void parentStudentForeignKeyOfSectionEnrollmentIsValidated() {
        Boolean validated = jdbcTemplate.queryForObject(
                "select convalidated from pg_constraint where conname = 'fk_subject_section_enrollment_parent_student'",
                Boolean.class);

        assertThat(validated).isTrue();
    }

    @Test
    @Disabled("E34-B-P8")
    void termProgramAndEvaluationSchemeCarryOptimisticLockVersion() {
        for (Class<?> entity : List.of(Term.class, Program.class, EvaluationScheme.class)) {
            assertThat(Arrays.stream(entity.getDeclaredFields()).anyMatch(field -> field.isAnnotationPresent(Version.class)))
                    .as(entity.getSimpleName()).isTrue();
        }
    }

    @Test
    @Disabled("E34-B-P8")
    void effectWithoutEventIdIsNotExecutedWithoutDeduplication() {
        DeduplicableDomainEvent eventWithoutId = new DeduplicableDomainEvent() {
            @Override
            public UUID eventId() {
                return null;
            }

            @Override
            public String correlationId() {
                return "reproduction";
            }

            @Override
            public Instant occurredAt() {
                return Instant.now();
            }
        };
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> {
            processedEffectGuard.runEffectOnce(eventWithoutId, "reproductionHandler", executions::incrementAndGet);
            processedEffectGuard.runEffectOnce(eventWithoutId, "reproductionHandler", executions::incrementAndGet);
        }).isInstanceOf(RuntimeException.class);
        assertThat(executions.get()).isLessThanOrEqualTo(1);
    }
}
