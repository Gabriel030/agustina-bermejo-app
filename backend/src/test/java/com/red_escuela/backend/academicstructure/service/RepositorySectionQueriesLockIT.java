package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.api.SectionQueries;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RepositorySectionQueriesLockIT extends AbstractIntegrationTest {

    @Autowired
    private SectionQueries sectionQueries;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void lockWithoutTransactionFails() {
        assertThatThrownBy(() -> sectionQueries.lockProgramSection(-1L))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> sectionQueries.lockCourseSection(-1L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void lockInsideTransactionReachesTheDatabase() {
        var transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> sectionQueries.lockProgramSection(-1L)))
                .isInstanceOf(SectionNotFoundException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> sectionQueries.lockCourseSection(-1L)))
                .isInstanceOf(SectionNotFoundException.class);
    }

    @Test
    void evaluationSchemeLookupOfMissingSectionFailsForEachKind() {
        assertThatThrownBy(() -> sectionQueries.findEvaluationSchemeId(SectionKind.PROGRAM, -1L))
                .isInstanceOf(SectionNotFoundException.class);
        assertThatThrownBy(() -> sectionQueries.findEvaluationSchemeId(SectionKind.COURSE, -1L))
                .isInstanceOf(SectionNotFoundException.class);
    }
}
