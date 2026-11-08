package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AcademicOfferingCycleReaderIT extends AbstractIntegrationTest {

    @Autowired
    private AcademicOfferingCycleReader cycleReader;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

    @Test
    void listingCycleSectionsLoadsSlotsInBatchAndKeepsEachSectionsOwnSlots() {
        int sectionCount = 6;
        Long cycleId = new TransactionTemplate(transactionManager).execute(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Program program = fixtures.program();
            Term term = fixtures.term(2032);
            AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.DRAFT);
            Classroom classroom = fixtures.classroom(30);
            Subject subject = fixtures.subject();
            for (int i = 0; i < sectionCount; i++) {
                SubjectSection section = fixtures.section(subject, term, StatusType.INACTIVE, cycle);
                fixtures.slot(section, classroom, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9 + i % 3, 0));
                if (i % 2 == 0) {
                    fixtures.slot(section, classroom, DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(10, 0));
                }
            }
            fixtures.flushAndClear();
            return cycle.getId();
        });

        List<CycleSectionResponse> sections = new TransactionTemplate(transactionManager).execute(status ->
                QueryCountAssertions.assertAtMostQueries(entityManagerFactory, 6 + sectionCount,
                        () -> cycleReader.listCycleSections(cycleId)));

        assertThat(sections).hasSize(sectionCount);
        assertThat(sections).extracting(section -> section.slots().size()).containsExactlyInAnyOrder(2, 2, 2, 1, 1, 1);
    }
}
