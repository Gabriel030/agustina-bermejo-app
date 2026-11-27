package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CycleSectionEvaluationConfigIT extends AbstractIntegrationTest {

    @Autowired
    private CreateCycleSectionUseCase createSection;
    @Autowired
    private SectionEvaluationConfigRepository configRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long cycleId;
    private Long subjectId;

    @BeforeEach
    void seedAnEditableCycleWithASubject() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            var term = fixtures.term(2100 + (int) (System.nanoTime() % 800));
            cycleId = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT).getId();
            subjectId = fixtures.subject().getId();
        });
    }

    @Test
    void cycleSectionIsBornWithADraftEvaluationConfigThatCanBeOpened() {
        var sectionId = createSection.createSection(cycleId,
                new CreateCycleSectionRequest("Commission A", subjectId, null, 20, null, null, SectionShift.MORNING, null));

        var config = configRepository.findBySubjectSectionId(sectionId);

        assertThat(config).isPresent();
        assertThat(config.get().getStatus()).isEqualTo(SectionEvaluationConfigStatus.DRAFT);
    }
}
