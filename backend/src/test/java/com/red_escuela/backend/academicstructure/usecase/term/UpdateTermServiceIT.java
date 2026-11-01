package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.InvalidTermDatesException;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UpdateTermServiceIT extends AbstractIntegrationTest {

    @Autowired
    private UpdateTermUseCase updateTerm;
    @Autowired
    private EntityManager entityManager;

    private Term term;
    private LocalDateTime originalStart;
    private LocalDateTime originalEnd;

    @BeforeEach
    void seedTerm() {
        term = new CycleTestFixtures(entityManager).term(2040 + (int) (System.nanoTime() % 50));
        originalStart = term.getStartDate();
        originalEnd = term.getEndDate();
        entityManager.flush();
    }

    @Test
    void updatingOnlyTheDescriptionKeepsTheScheduleAndTheEnrollmentWindow() {
        Term updated = updateTerm.update(new UpdateTermCommand(term.getId(),
                new UpdateTermRequest("Nueva descripcion", null, null, null, null)));

        assertThat(updated.getDescription()).isEqualTo("Nueva descripcion");
        assertThat(updated.getStartDate()).isEqualTo(originalStart);
        assertThat(updated.getEndDate()).isEqualTo(originalEnd);
        assertThat(updated.getEnrollmentStart()).isNull();
        assertThat(updated.getEnrollmentEnd()).isNull();
    }

    @Test
    void leavingTheDescriptionBlankKeepsTheCurrentOne() {
        String current = term.getDescription();

        Term updated = updateTerm.update(new UpdateTermCommand(term.getId(),
                new UpdateTermRequest(null, null, null, null, null)));

        assertThat(updated.getDescription()).isEqualTo(current);
    }

    @Test
    void movingOnlyTheEndDateKeepsTheStartDate() {
        LocalDateTime newEnd = originalEnd.plusDays(10);

        Term updated = updateTerm.update(new UpdateTermCommand(term.getId(),
                new UpdateTermRequest(null, null, newEnd, null, null)));

        assertThat(updated.getStartDate()).isEqualTo(originalStart);
        assertThat(updated.getEndDate()).isEqualTo(newEnd);
    }

    @Test
    void anEndDateBeforeTheStartDateIsRejected() {
        assertThatThrownBy(() -> updateTerm.update(new UpdateTermCommand(term.getId(),
                new UpdateTermRequest(null, null, originalStart.minusDays(1), null, null))))
                .isInstanceOf(InvalidTermDatesException.class);
    }

    @Test
    void definingTheEnrollmentWindowStoresBothBoundaries() {
        LocalDateTime opens = originalStart.minusDays(30);
        LocalDateTime closes = originalStart.plusDays(15);

        Term updated = updateTerm.update(new UpdateTermCommand(term.getId(),
                new UpdateTermRequest(null, null, null, opens, closes)));

        assertThat(updated.getEnrollmentStart()).isEqualTo(opens);
        assertThat(updated.getEnrollmentEnd()).isEqualTo(closes);
    }

    @Test
    void updatingAnUnknownTermReturnsNotFound() {
        assertThatThrownBy(() -> updateTerm.update(new UpdateTermCommand(987_654_321L,
                new UpdateTermRequest("x", null, null, null, null))))
                .isInstanceOf(TermNotFoundException.class);
    }
}
