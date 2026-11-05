package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.dto.UpdateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.InvalidTermDatesException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TermUpdateIT extends AbstractIntegrationTest {

    @Autowired
    private TermUpdate termUpdate;
    @Autowired
    private TermRepository termRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void updateTermPersistsDescriptionAndEndDateThroughDirtyChecking() {
        Long id = savedTerm(2071);

        termUpdate.update(id, new UpdateTermRequest("renamed", null, LocalDateTime.of(2999, 6, 30, 23, 59), null, null));

        assertThat(jdbcTemplate.queryForObject("select description from term where id = ?", String.class, id)).isEqualTo("renamed");
        assertThat(jdbcTemplate.queryForObject("select end_date from term where id = ?", LocalDateTime.class, id))
                .isEqualTo(LocalDateTime.of(2999, 6, 30, 23, 59));
    }

    @Test
    void updateTermLeavesTermUnchangedWhenEndDateIsBeforeStartDate() {
        Long id = savedTerm(2072);

        assertThatThrownBy(() -> termUpdate.update(id, new UpdateTermRequest("renamed", null, LocalDateTime.of(1999, 1, 1, 0, 0), null, null)))
                .isInstanceOf(InvalidTermDatesException.class);

        assertThat(jdbcTemplate.queryForObject("select description from term where id = ?", String.class, id)).isEqualTo("original");
    }

    private Long savedTerm(int year) {
        Term term = TermFixtures.newTerm();
        term.setYear(year);
        term.setDescription("original");
        term.setType(TermType.FIRST_SEMESTER);
        term.setStatus(StatusType.ACTIVE);
        return termRepository.save(term).getId();
    }
}
