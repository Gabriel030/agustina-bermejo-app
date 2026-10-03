package com.red_escuela.backend.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.TeachingSectionResponse;
import com.red_escuela.backend.entity.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeachingSectionServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TeachingSectionService teachingSectionService;

    @Autowired
    private EntityManager entityManager;

    private CycleTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CycleTestFixtures(entityManager);
    }

    private SubjectSection leadSectionInCycle(Instructor instructor, Term term, AcademicOfferingCycleStatus cycleStatus, StatusType sectionStatus) {
        var cycle = fixtures.programCycle(fixtures.program(), term, cycleStatus);
        SubjectSection section = fixtures.section(fixtures.subject(), term, sectionStatus, cycle);
        fixtures.assignLead(section, instructor);
        return section;
    }

    @Test
    void sectionsOfCyclesNotYetApprovedAreNotListedAsTheInstructorsOwn() {
        Instructor instructor = fixtures.instructor();
        Term term = fixtures.term(2027);
        leadSectionInCycle(instructor, term, AcademicOfferingCycleStatus.DRAFT, StatusType.INACTIVE);
        leadSectionInCycle(instructor, term, AcademicOfferingCycleStatus.PENDING_APPROVAL, StatusType.INACTIVE);
        leadSectionInCycle(instructor, term, AcademicOfferingCycleStatus.REJECTED, StatusType.INACTIVE);
        fixtures.flushAndClear();

        assertThat(teachingSectionService.listByInstructor(instructor.getId())).isEmpty();
    }

    @Test
    void sectionsOfApprovedCyclesAndSectionsOutsideAnyCycleAreListed() {
        Instructor instructor = fixtures.instructor();
        Term term = fixtures.term(2027);
        SubjectSection approved = leadSectionInCycle(instructor, term, AcademicOfferingCycleStatus.APPROVED, StatusType.ACTIVE);
        SubjectSection standalone = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, null);
        fixtures.assignLead(standalone, instructor);
        fixtures.flushAndClear();

        assertThat(teachingSectionService.listByInstructor(instructor.getId()))
                .extracting(TeachingSectionResponse::id)
                .containsExactlyInAnyOrder(approved.getId(), standalone.getId());
    }
}
