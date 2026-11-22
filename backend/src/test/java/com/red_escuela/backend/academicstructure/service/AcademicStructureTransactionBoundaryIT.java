package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.dto.CreateAcademicOfferingCycleRequest;
import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;
import com.red_escuela.backend.academicstructure.dto.ScheduleSlotRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.exception.SectionCapacityExceedsClassroomException;
import com.red_escuela.backend.academicstructure.usecase.cycle.CreateAcademicOfferingCycleUseCase;
import com.red_escuela.backend.academicstructure.usecase.cycle.CreateCycleSectionUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AcademicStructureTransactionBoundaryIT extends AbstractIntegrationTest {

    private static final int CLOSED_YEAR = 2020;

    @Autowired
    private CreateCycleSectionUseCase createCycleSection;

    @Autowired
    private CreateAcademicOfferingCycleUseCase createAcademicOfferingCycle;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void sectionCreationFailingOnItsSecondSlotLeavesNeitherTheSectionNorTheFirstSlot() {
        long[] ids = new long[4];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            var term = fixtures.term(2040);
            ids[0] = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT).getId();
            ids[1] = fixtures.subject().getId();
            ids[2] = fixtures.classroom(30).getId();
            ids[3] = fixtures.classroom(5).getId();
        });
        var request = new CreateCycleSectionRequest("Comision A", ids[1], null, 20,
                List.of(new ScheduleSlotRequest(ids[2], DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)),
                        new ScheduleSlotRequest(ids[3], DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0))),
                null, SectionShift.MORNING, null);
        long sectionsBefore = count("subject_section");
        long slotsBefore = count("section_schedule_slot");

        assertThatThrownBy(() -> createCycleSection.createSection(ids[0], request))
                .isInstanceOf(SectionCapacityExceedsClassroomException.class);

        assertThat(count("subject_section")).isEqualTo(sectionsBefore);
        assertThat(count("section_schedule_slot")).isEqualTo(slotsBefore);
    }

    @Test
    void cycleCreationWithoutAnOuterTransactionPersistsExactlyOneCycleEvenWhenOtherTermsAlreadyHaveOne() {
        long[] ids = new long[3];
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            var program = fixtures.program();
            var otherTerm = fixtures.term(CLOSED_YEAR);
            fixtures.programCycle(program, otherTerm, AcademicOfferingCycleStatus.APPROVED);
            ids[0] = program.getId();
            ids[1] = fixtures.term(CLOSED_YEAR - 1).getId();
            ids[2] = fixtures.person().getId();
        });
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(ids[2]), null));
        long cyclesBefore = count("academic_offering_cycle");

        var response = createAcademicOfferingCycle.create(new CreateAcademicOfferingCycleRequest(ids[0], null, ids[1]));

        assertThat(response.termId()).isEqualTo(ids[1]);
        assertThat(count("academic_offering_cycle")).isEqualTo(cyclesBefore + 1);
    }
}
