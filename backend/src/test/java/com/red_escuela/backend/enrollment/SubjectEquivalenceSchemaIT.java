package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubjectEquivalenceSchemaIT extends AbstractIntegrationTest {

    private static final String INSERT = "insert into subject_equivalence (program_enrollment_id, subject_id, origin_institution, origin_subject_name, "
            + "origin_score, recognized_on, resolution_number, reason, status, recorded_by_id, recorded_at, voided_by_id, voided_at, void_reason) "
            + "values (?, ?, 'Universidad de origen', 'Materia de origen', ?, current_date, ?, 'Estudios previos', ?, ?, now(), ?, ?, ?)";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SubjectEquivalenceRepository repository;

    private CycleTestFixtures fixtures;
    private ProgramEnrollment enrollment;
    private ProgramEnrollment otherEnrollment;
    private Subject subject;
    private Subject otherSubject;
    private Person actor;

    @BeforeEach
    void seed() {
        fixtures = new CycleTestFixtures(entityManager);
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        var program = fixtures.program();
        enrollment = programEnrollment(institution, program);
        otherEnrollment = programEnrollment(institution, program);
        subject = fixtures.subject();
        otherSubject = fixtures.subject();
        actor = fixtures.person();
        entityManager.flush();
    }

    @Test
    void voidedAndActiveEquivalencesOfTheSameSubjectCoexist() {
        insert(enrollment, subject, "5.00", "RES-1", "VOIDED", true);
        insert(enrollment, subject, "5.00", "RES-2", "ACTIVE", false);

        assertThat(jdbc.queryForObject("select count(*) from subject_equivalence where program_enrollment_id = ?", Integer.class, enrollment.getId())).isEqualTo(2);
    }

    @Test
    void twoActiveEquivalencesOfTheSameSubjectAndEnrollmentAreRejected() {
        insert(enrollment, subject, "5.00", "RES-1", "ACTIVE", false);

        assertThatThrownBy(() -> insert(enrollment, subject, "5.00", "RES-2", "ACTIVE", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeEquivalenceOfTheSameSubjectInAnotherEnrollmentIsAllowed() {
        insert(enrollment, subject, "5.00", "RES-1", "ACTIVE", false);
        insert(otherEnrollment, subject, "5.00", "RES-2", "ACTIVE", false);

        assertThat(jdbc.queryForObject("select count(*) from subject_equivalence", Integer.class)).isEqualTo(2);
    }

    @Test
    void originScoreAboveTenIsRejected() {
        assertThatThrownBy(() -> insert(enrollment, subject, "10.01", "RES-1", "ACTIVE", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void originScoreBelowOneIsRejected() {
        assertThatThrownBy(() -> insert(enrollment, subject, "0.99", "RES-1", "ACTIVE", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void originScoreZeroIsRejected() {
        assertThatThrownBy(() -> insert(enrollment, subject, "0.00", "RES-1", "ACTIVE", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void missingReasonIsRejected() {
        assertThatThrownBy(() -> jdbc.update("insert into subject_equivalence (program_enrollment_id, subject_id, origin_institution, origin_subject_name, "
                + "recognized_on, resolution_number, status, recorded_by_id, recorded_at) values (?, ?, 'U', 'M', current_date, 'R', 'ACTIVE', ?, now())",
                enrollment.getId(), subject.getId(), actor.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void originScoreBoundariesAreAccepted() {
        insert(enrollment, subject, "1.00", "RES-1", "ACTIVE", false);
        insert(enrollment, otherSubject, "10.00", "RES-2", "ACTIVE", false);

        assertThat(jdbc.queryForObject("select count(*) from subject_equivalence", Integer.class)).isEqualTo(2);
    }

    @Test
    void blankResolutionNumberIsRejected() {
        assertThatThrownBy(() -> insert(enrollment, subject, "5.00", "   ", "ACTIVE", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> insert(enrollment, subject, "5.00", "RES-1", "PENDING", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void voidedEquivalenceWithoutVoidDataIsRejected() {
        assertThatThrownBy(() -> insert(enrollment, subject, "5.00", "RES-1", "VOIDED", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void activeEquivalenceWithVoidDataIsRejected() {
        assertThatThrownBy(() -> insert(enrollment, subject, "5.00", "RES-1", "ACTIVE", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void voidedEquivalenceWithPartialVoidDataIsRejected() {
        assertThatThrownBy(() -> jdbc.update(INSERT, enrollment.getId(), subject.getId(), new BigDecimal("5.00"), "RES-1", "VOIDED",
                actor.getId(), actor.getId(), null, "motivo"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void auditLogAcceptsTheEquivalenceActionsAndEntityType() {
        jdbc.update("insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), 'SUBJECT_EQUIVALENCE_RECORDED', 'SUBJECT_EQUIVALENCE', 1)");
        jdbc.update("insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), 'SUBJECT_EQUIVALENCE_VOIDED', 'SUBJECT_EQUIVALENCE', 1)");

        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'SUBJECT_EQUIVALENCE'", Integer.class)).isEqualTo(2);
    }

    @Test
    void auditLogStillAcceptsEveryPreviousActionAndEntityType() {
        for (AuditAction action : AuditAction.values()) {
            jdbc.update("insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), ?, 'PERSON', 1)", action.name());
        }
        for (AuditEntityType entityType : AuditEntityType.values()) {
            jdbc.update("insert into audit_log (occurred_at, action, entity_type, entity_id) values (now(), 'STAFF_WITHDRAWN', ?, 1)", entityType.name());
        }

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'DOCUMENT_DEBT_EXC_ISSUED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'DOCUMENT_DEBT_EXCEPTION'", Integer.class)).isEqualTo(1);
    }

    @Test
    void repositoryRoundTripKeepsBoundaryScores() {
        SubjectEquivalence lowest = equivalence(enrollment, subject, "RES-1");
        lowest.setOriginScore(Grade.of(new BigDecimal("1.00")));
        SubjectEquivalence highest = equivalence(enrollment, otherSubject, "RES-2");
        highest.setOriginScore(Grade.of(new BigDecimal("10.00")));
        repository.saveAndFlush(lowest);
        repository.saveAndFlush(highest);
        entityManager.clear();

        assertThat(repository.findById(lowest.getId()).orElseThrow().getOriginScore()).isEqualTo(Grade.of(new BigDecimal("1.00")));
        assertThat(repository.findById(highest.getId()).orElseThrow().getOriginScore()).isEqualTo(Grade.of(new BigDecimal("10.00")));
    }

    @Test
    void versionIncrementsWhenTheEquivalenceIsUpdated() {
        SubjectEquivalence saved = repository.saveAndFlush(equivalence(enrollment, subject, "RES-1"));
        Long initialVersion = saved.getVersion();

        saved.setStatus(SubjectEquivalenceStatus.VOIDED);
        saved.setVoidedById(actor.getId());
        saved.setVoidedAt(LocalDateTime.now());
        saved.setVoidReason("error de carga");
        repository.saveAndFlush(saved);

        assertThat(saved.getVersion()).isGreaterThan(initialVersion);
    }

    @Test
    void permissionsAreGrantedToPreceptorAndAdministrativeAndAreSensitiveWhenTheyWrite() {
        assertThat(jdbc.queryForList("select role from role_permission rp join permission p on p.id = rp.permission_id where p.code = 'enrollment.read_subject_equivalences'", String.class))
                .containsExactlyInAnyOrder("PRECEPTOR", "ADMINISTRATIVO");
        assertThat(jdbc.queryForList("select role from role_permission rp join permission p on p.id = rp.permission_id where p.code = 'enrollment.record_subject_equivalence'", String.class))
                .containsExactly("ADMINISTRATIVO");
        assertThat(jdbc.queryForList("select role from role_permission rp join permission p on p.id = rp.permission_id where p.code = 'enrollment.void_subject_equivalence'", String.class))
                .containsExactly("ADMINISTRATIVO");
        assertThat(jdbc.queryForList("select code from permission where code like 'enrollment.%subject_equivalence%' and sensitive and not fixed_to_director order by code", String.class))
                .containsExactly("enrollment.record_subject_equivalence", "enrollment.void_subject_equivalence");
    }

    @Test
    void entityRoundTripKeepsDefaultsAndScore() {
        SubjectEquivalence saved = repository.saveAndFlush(equivalence(enrollment, subject, "RES-1"));
        entityManager.clear();

        SubjectEquivalence found = repository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(SubjectEquivalenceStatus.ACTIVE);
        assertThat(found.getOriginScore()).isEqualTo(Grade.of(new BigDecimal("7.50")));
        assertThat(found.getVersion()).isNotNull();
    }

    @Test
    void activeEquivalenceOfAnEnrollmentAndSubjectIsFoundIgnoringVoidedOnes() {
        SubjectEquivalence voided = equivalence(enrollment, subject, "RES-1");
        voided.setStatus(SubjectEquivalenceStatus.VOIDED);
        voided.setVoidedById(actor.getId());
        voided.setVoidedAt(LocalDateTime.now());
        voided.setVoidReason("error de carga");
        repository.save(voided);
        SubjectEquivalence active = repository.save(equivalence(enrollment, subject, "RES-2"));
        entityManager.flush();

        assertThat(repository.findByProgramEnrollmentIdAndSubjectIdAndStatus(enrollment.getId(), subject.getId(), SubjectEquivalenceStatus.ACTIVE))
                .get().extracting(SubjectEquivalence::getId).isEqualTo(active.getId());
        assertThat(repository.findByProgramEnrollmentIdAndSubjectIdAndStatus(enrollment.getId(), otherSubject.getId(), SubjectEquivalenceStatus.ACTIVE)).isEmpty();
    }

    @Test
    void listingByEnrollmentRespectsTheStatusFilter() {
        SubjectEquivalence voided = equivalence(enrollment, subject, "RES-1");
        voided.setStatus(SubjectEquivalenceStatus.VOIDED);
        voided.setVoidedById(actor.getId());
        voided.setVoidedAt(LocalDateTime.now());
        voided.setVoidReason("error de carga");
        repository.save(voided);
        repository.save(equivalence(enrollment, subject, "RES-2"));
        repository.save(equivalence(otherEnrollment, otherSubject, "RES-3"));
        entityManager.flush();

        assertThat(repository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId())).hasSize(2);
        assertThat(repository.findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(enrollment.getId(), SubjectEquivalenceStatus.ACTIVE))
                .extracting(SubjectEquivalence::getResolutionNumber).containsExactly("RES-2");
    }

    @Test
    void activeEquivalencesOfSeveralEnrollmentsAreLoadedInOneBatch() {
        repository.save(equivalence(enrollment, subject, "RES-1"));
        repository.save(equivalence(otherEnrollment, otherSubject, "RES-2"));
        SubjectEquivalence voided = equivalence(otherEnrollment, subject, "RES-3");
        voided.setStatus(SubjectEquivalenceStatus.VOIDED);
        voided.setVoidedById(actor.getId());
        voided.setVoidedAt(LocalDateTime.now());
        voided.setVoidReason("error de carga");
        repository.save(voided);
        entityManager.flush();
        entityManager.clear();

        List<SubjectEquivalence> found = repository.findByProgramEnrollmentIdsAndStatus(List.of(enrollment.getId(), otherEnrollment.getId()), SubjectEquivalenceStatus.ACTIVE);

        assertThat(found).extracting(SubjectEquivalence::getResolutionNumber).containsExactlyInAnyOrder("RES-1", "RES-2");
        assertThat(repository.findByProgramEnrollmentIdsAndStatus(List.of(enrollment.getId()), SubjectEquivalenceStatus.ACTIVE))
                .extracting(SubjectEquivalence::getResolutionNumber).containsExactly("RES-1");
    }

    private void insert(ProgramEnrollment target, Subject targetSubject, String score, String resolution, String status, boolean withVoidData) {
        jdbc.update(INSERT, target.getId(), targetSubject.getId(), new BigDecimal(score), resolution, status, actor.getId(),
                withVoidData ? actor.getId() : null, withVoidData ? LocalDateTime.now() : null, withVoidData ? "motivo" : null);
    }

    private SubjectEquivalence equivalence(ProgramEnrollment target, Subject targetSubject, String resolution) {
        return SubjectEquivalence.builder()
                .programEnrollment(target)
                .subjectId(targetSubject.getId())
                .originInstitution("Universidad de origen")
                .originSubjectName("Materia de origen")
                .originScore(Grade.of(new BigDecimal("7.50")))
                .recognizedOn(LocalDate.now())
                .resolutionNumber(resolution)
                .reason("Estudios previos")
                .recordedById(actor.getId())
                .recordedAt(LocalDateTime.now())
                .build();
    }

    private ProgramEnrollment programEnrollment(Institution institution, com.red_escuela.backend.academicstructure.entity.Program program) {
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        ProgramEnrollment created = new ProgramEnrollment();
        created.setStudent(student);
        created.setProgram(program);
        created.setStatus(ProgramEnrollmentStatus.ENROLLED);
        created.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(created);
        return created;
    }
}
