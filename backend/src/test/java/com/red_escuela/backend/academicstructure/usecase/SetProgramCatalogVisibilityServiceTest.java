package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.entity.CatalogArea;
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
        assertThatThrownBy(() -> setCatalogVisibility.execute(new SetCatalogVisibilityCommand(999999L, true, null)))
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

        var response = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(program.getId(), true, null));
        assertThat(response.isVisibleInCatalog()).isTrue();

        response = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(program.getId(), false, null));
        assertThat(response.isVisibleInCatalog()).isFalse();
    }

    @Test
    void firstPublicationSetsCatalogPublishedAtAndRepublishingKeepsIt() {
        Program program = new Program();
        program.setName("Tecnicatura Primera Publicacion");
        program.setTotalYears(3);
        entityManager.persist(program);
        entityManager.flush();

        var hidden = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(program.getId(), false, null));
        assertThat(hidden.getCatalogPublishedAt()).isNull();

        var firstPublication = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(program.getId(), true, null));
        assertThat(firstPublication.getCatalogPublishedAt()).isNotNull();

        setCatalogVisibility.execute(new SetCatalogVisibilityCommand(program.getId(), false, null));
        var republication = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(program.getId(), true, null));
        assertThat(republication.getCatalogPublishedAt()).isEqualTo(firstPublication.getCatalogPublishedAt());
    }

    @Test
    void areaIsStoredWhenGivenAndKeptWhenOmitted() {
        Program program = new Program();
        program.setName("Tecnicatura Area");
        program.setTotalYears(3);
        entityManager.persist(program);
        entityManager.flush();

        var withArea = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(program.getId(), true, CatalogArea.DESIGN));
        assertThat(withArea.getArea()).isEqualTo("DESIGN");

        var withoutArea = setCatalogVisibility.execute(new SetCatalogVisibilityCommand(program.getId(), false, null));
        assertThat(withoutArea.getArea()).isEqualTo("DESIGN");
    }
}
