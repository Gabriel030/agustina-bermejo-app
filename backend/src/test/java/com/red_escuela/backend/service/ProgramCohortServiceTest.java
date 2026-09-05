package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.exception.InvalidProgramCohortException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Épica 8 Fase 2: política institucional de exactamente 2 cohortes por carrera
 * por año (ordenEnAnio 1 o 2, sin repetir).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramCohortServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ProgramCohortService programCohortService;

    @Autowired
    private EntityManager entityManager;

    private Program persistPrograma() {
        Program program = new Program();
        program.setName("Diseño Gráfico " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        entityManager.persist(program);
        return program;
    }

    @Test
    void crearCohorteConOrdenValidoFunciona() {
        Program program = persistPrograma();
        entityManager.flush();

        ProgramCohort cohort = programCohortService.create(
                new CreateProgramCohortRequest(program.getId(), "Marzo 2026", 1, null, null, LocalDate.of(2026, 3, 1)));

        assertThat(cohort.getId()).isNotNull();
        assertThat(cohort.getOrdenEnAnio()).isEqualTo(1);
    }

    @Test
    void crearCohorteConOrdenFueraDeUnoODosFalla() {
        Program program = persistPrograma();
        entityManager.flush();

        assertThatThrownBy(() -> programCohortService.create(
                new CreateProgramCohortRequest(program.getId(), "Inválida", 3, null, null, LocalDate.of(2026, 3, 1))))
                .isInstanceOf(InvalidProgramCohortException.class);
    }

    @Test
    void crearDosCohortesConElMismoOrdenParaLaMismaCarreraFalla() {
        Program program = persistPrograma();
        entityManager.flush();

        programCohortService.create(
                new CreateProgramCohortRequest(program.getId(), "Marzo 2026", 1, null, null, LocalDate.of(2026, 3, 1)));

        assertThatThrownBy(() -> programCohortService.create(
                new CreateProgramCohortRequest(program.getId(), "Marzo otra vez", 1, null, null, LocalDate.of(2026, 3, 15))))
                .isInstanceOf(InvalidProgramCohortException.class);
    }

    @Test
    void crearCohorteConVentanaMalConfiguradaFalla() {
        Program program = persistPrograma();
        entityManager.flush();

        LocalDateTime start = LocalDateTime.now();
        LocalDateTime end = start.minusDays(1);

        assertThatThrownBy(() -> programCohortService.create(
                new CreateProgramCohortRequest(program.getId(), "Marzo 2026", 1, start, end, LocalDate.of(2026, 3, 1))))
                .isInstanceOf(InvalidProgramCohortException.class);
    }

    @Test
    void unaCarreraPuedeTenerLasDosCohortesDelAnio() {
        Program program = persistPrograma();
        entityManager.flush();

        programCohortService.create(
                new CreateProgramCohortRequest(program.getId(), "Marzo 2026", 1, null, null, LocalDate.of(2026, 3, 1)));
        programCohortService.create(
                new CreateProgramCohortRequest(program.getId(), "Agosto 2026", 2, null, null, LocalDate.of(2026, 8, 1)));

        assertThat(programCohortService.listByProgram(program.getId())).hasSize(2);
    }
}
