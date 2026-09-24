package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SetProgramCatalogVisibilityServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SetProgramCatalogVisibilityUseCase setCatalogVisibility;

    @Autowired
    private EntityManager entityManager;

    @Test
    void nonExistentProgramThrowsProgramNotFoundException() {
        assertThatThrownBy(() -> setCatalogVisibility.execute(999999L, true))
                .isInstanceOf(ProgramNotFoundException.class);
    }

    @Test
    void programVisibilityIsUpdatedCorrectly() {
        Program program = new Program();
        program.setName("Tecnicatura Visibilidad");
        program.setTotalYears(3);
        entityManager.persist(program);
        entityManager.flush();

        assertThat(program.isVisibleInCatalog()).isFalse();

        var response = setCatalogVisibility.execute(program.getId(), true);
        assertThat(response.isVisibleInCatalog()).isTrue();

        response = setCatalogVisibility.execute(program.getId(), false);
        assertThat(response.isVisibleInCatalog()).isFalse();
    }
}
