package com.red_escuela.backend.academicstructure.repository;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TermDatesConstraintTest extends AbstractIntegrationTest {
    @Autowired
    private EntityManager entityManager;

    @Test
    void databaseRejectsATermEndingBeforeItStartsEvenBypassingTheDomain() {
        Term term = new Term();
        term.setYear(2027);
        term.setDescription("Período invertido por fuera del dominio");
        term.setStartDate(LocalDateTime.of(2027, 12, 31, 0, 0));
        term.setEndDate(LocalDateTime.of(2027, 1, 1, 0, 0));

        assertThatThrownBy(() -> {
            entityManager.persist(term);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }
}
