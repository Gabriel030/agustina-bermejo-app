package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.exception.SectionCodeAlreadyExistsException;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
class SectionCodeGeneratorIT extends AbstractIntegrationTest {

    @Autowired
    private SectionCodeGenerator generator;
    @Autowired
    private SubjectSectionRepository subjectSectionRepository;
    @Autowired
    private EntityManager entityManager;

    private CycleTestFixtures fixtures;
    private String prefix;

    @BeforeEach
    void setUp() {
        fixtures = new CycleTestFixtures(entityManager);
        prefix = "Z" + Long.toString(System.nanoTime() % 100000, 36).toUpperCase();
    }

    @Test
    void codesAreCorrelativePerPrefixAndPeriod() {
        var term = fixtures.term(2051);

        assertThat(generator.nextCodeForSubject(prefix, term)).isEqualTo(prefix + "-2051A-01");
        assertThat(generator.nextCodeForSubject(prefix, term)).isEqualTo(prefix + "-2051A-02");
        assertThat(generator.nextCodeForSubject(prefix + "X", term)).isEqualTo(prefix + "X-2051A-01");
        assertThat(generator.nextCodeForSubject(prefix, fixtures.term(2052))).isEqualTo(prefix + "-2052A-01");
    }

    @Test
    void courseSubjectCodesUseTheCourseSubjectPrefix() {
        var term = fixtures.term(2053);

        assertThat(generator.nextCodeForCourseSubject(987654L, term)).isEqualTo("CM987654-2053A-01");
    }

    @Test
    void periodSuffixDependsOnTheTermType() {
        var first = fixtures.term(2054);
        first.setType(com.red_escuela.backend.academicstructure.domain.TermType.FIRST_SEMESTER);
        var second = fixtures.term(2054);
        second.setType(com.red_escuela.backend.academicstructure.domain.TermType.SECOND_SEMESTER);
        var intensive = fixtures.term(2054);
        intensive.setType(com.red_escuela.backend.academicstructure.domain.TermType.INTENSIVE);

        assertThat(generator.nextCodeForSubject(prefix, first)).isEqualTo(prefix + "-2054C1-01");
        assertThat(generator.nextCodeForSubject(prefix, second)).isEqualTo(prefix + "-2054C2-01");
        assertThat(generator.nextCodeForSubject(prefix, intensive)).isEqualTo(prefix + "-2054V-01");
    }

    @Test
    void codeAlreadyUsedByAnExistingSectionIsRejected() {
        var term = fixtures.term(2055);
        var subject = fixtures.subject();
        var section = fixtures.section(subject, term, StatusType.ACTIVE, null);
        entityManager.flush();
        entityManager.createNativeQuery("update subject_section set code = :code where id = :id")
                .setParameter("code", prefix + "-2055A-01")
                .setParameter("id", section.getId())
                .executeUpdate();

        assertThatThrownBy(() -> generator.nextCodeForSubject(prefix, term))
                .isInstanceOf(SectionCodeAlreadyExistsException.class);
        assertThat(subjectSectionRepository.existsByCode(prefix + "-2055A-01")).isTrue();
    }
}
