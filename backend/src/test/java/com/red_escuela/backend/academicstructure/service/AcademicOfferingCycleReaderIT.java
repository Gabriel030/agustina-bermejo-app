package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AcademicOfferingCycleReaderIT extends AbstractIntegrationTest {

    @Autowired
    private AcademicOfferingCycleReader cycleReader;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void cycleResponsesAreResolvedWithoutAnOuterTransaction() {
        AcademicOfferingCycle cycle = new TransactionTemplate(transactionManager).execute(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Program program = fixtures.program();
            Term term = fixtures.term(2031);
            return fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        });

        var single = cycleReader.readCycle(cycle.getId());
        var listed = cycleReader.listCycles(null, null, AcademicOfferingCycleStatus.APPROVED);
        var sections = cycleReader.listCycleSections(cycle.getId());

        assertThat(single.createdByName()).isNotBlank();
        assertThat(single.programName()).isNotBlank();
        assertThat(listed).extracting("id").contains(cycle.getId());
        assertThat(sections).isEmpty();
    }
}
