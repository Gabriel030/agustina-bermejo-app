package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.api.CurriculumPlanFinder;
import com.red_escuela.backend.academicstructure.domain.CurriculumPlanNotOpenForNewStudentsException;
import com.red_escuela.backend.academicstructure.dto.AddCorequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CreateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.ExpireCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlanStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCategory;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.domain.InvalidCurriculumPlanTransitionException;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.ActivateCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.CreateCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.ExpireCurriculumPlanUseCase;
import com.red_escuela.backend.academicstructure.usecase.curriculumplan.UpdateCurriculumPlanUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CurriculumPlanVersioningIT extends AbstractIntegrationTest {
    @Autowired
    private ProgramSubjectService programSubjectService;
    @Autowired
    private CreateCurriculumPlanUseCase createCurriculumPlanUseCase;
    @Autowired
    private UpdateCurriculumPlanUseCase updateCurriculumPlanUseCase;
    @Autowired
    private ActivateCurriculumPlanUseCase activateCurriculumPlanUseCase;
    @Autowired
    private ExpireCurriculumPlanUseCase expireCurriculumPlanUseCase;
    @Autowired
    private CurriculumPlanRepository curriculumPlanRepository;
    @Autowired
    private ProgramSubjectRepository programSubjectRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CurriculumPlanFinder curriculumPlanFinder;
    @Autowired
    private java.time.Clock clock;
    @Autowired
    private EntityManager entityManager;

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        entityManager.flush();
        return program;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        entityManager.flush();
        return subject;
    }

    private ProgramSubject addSubject(Program program, Subject subject, int year) {
        return programSubjectService.addSubject(program.getId(), new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.MANDATORY, year, CurricularTerm.FIRST, 10, null, null, null));
    }

    private CreateCurriculumPlanRequest draftRequest(Long copyFrom) {
        return new CreateCurriculumPlanRequest("Plan 2028", LocalDate.of(2028, 3, 1), null, null, null, copyFrom);
    }

    @Test
    void creatingNewPlanVersionLeavesTheCurrentPlanAndItsStudentsUntouched() {
        Program program = persistProgram("Tecnicatura Versionada");
        ProgramSubject first = addSubject(program, persistSubject("Anatomía"), 1);
        ProgramSubject second = addSubject(program, persistSubject("Fisiología"), 2);
        programSubjectService.addPrerequisite(second.getId(),
                new AddPrerequisiteRequest(first.getId(), RequirementType.APPROVED, null, null));
        entityManager.flush();
        Long currentPlanId = first.getPlan().getId();

        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(), draftRequest(currentPlanId));
        entityManager.flush();
        entityManager.clear();

        CurriculumPlan current = curriculumPlanRepository.findCurrentByProgramId(program.getId()).orElseThrow();
        assertThat(current.getId()).isEqualTo(currentPlanId);
        assertThat(current.getStatus()).isEqualTo(CurriculumPlanStatus.ACTIVE);
        assertThat(current.getTotalCreditsRequired()).isEqualTo(100);
        assertThat(programSubjectRepository.findByProgramId(program.getId()))
                .extracting(ProgramSubject::getId).containsExactly(first.getId(), second.getId());
        assertThat(draft.status()).isEqualTo(CurriculumPlanStatus.DRAFT);
        assertThat(draft.versionNumber()).isEqualTo(2);
        assertThat(draft.totalCreditsRequired()).isEqualTo(100);
        assertThat(programSubjectRepository.findByPlanIdOrderById(draft.id())).hasSize(2)
                .allSatisfy(copy -> assertThat(copy.getId()).isNotIn(first.getId(), second.getId()));
        assertThat(jdbc.queryForObject("select count(*) from program_subject_prerequisite p join program_subject ps "
                + "on ps.id = p.program_subject_id where ps.plan_id = ?", Integer.class, draft.id())).isEqualTo(1);
    }

    @Test
    void sameSubjectCanLiveInTwoPlanVersionsOfTheSameProgram() {
        Program program = persistProgram("Tecnicatura Dos Versiones");
        Subject subject = persistSubject("Química");
        addSubject(program, subject, 1);
        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(), draftRequest(null));

        ProgramSubject inDraft = programSubjectService.addSubjectToPlan(draft.id(), new CreateProgramSubjectRequest(
                subject.getId(), ProgramSubjectCategory.MANDATORY, 1, CurricularTerm.FIRST, 8, null, null, null));

        assertThat(inDraft.getPlan().getId()).isEqualTo(draft.id());
        assertThat(programSubjectService.listByProgram(program.getId(), null)).hasSize(1);
    }

    @Test
    void draftPlanTotalsCanBeEditedAndActivationMakesItTheCurrentPlan() {
        Program program = persistProgram("Tecnicatura Activación");
        addSubject(program, persistSubject("Biología"), 1);
        Long currentPlanId = curriculumPlanRepository.findCurrentByProgramId(program.getId()).orElseThrow().getId();
        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(), draftRequest(currentPlanId));

        updateCurriculumPlanUseCase.update(draft.id(), new UpdateCurriculumPlanRequest(null, null, null, 4, 150, null));
        activateCurriculumPlanUseCase.activate(draft.id());
        entityManager.flush();
        entityManager.clear();

        CurriculumPlan current = curriculumPlanRepository.findCurrentByProgramId(program.getId()).orElseThrow();
        assertThat(current.getId()).isEqualTo(draft.id());
        assertThat(current.getTotalYears()).isEqualTo(4);
        CurriculumPlan previous = curriculumPlanRepository.findById(currentPlanId).orElseThrow();
        assertThat(previous.getStatus()).isEqualTo(CurriculumPlanStatus.EXPIRED);
        assertThat(previous.getValidUntil()).isNotNull();
        assertThat(entityManager.find(Program.class, program.getId()).getTotalCreditsRequired()).isEqualTo(150);
    }

    @Test
    void expiredPlanCannotBeActivatedAndRecordsGraduationDeadline() {
        Program program = persistProgram("Tecnicatura Vencida");
        addSubject(program, persistSubject("Física"), 1);
        Long planId = curriculumPlanRepository.findCurrentByProgramId(program.getId()).orElseThrow().getId();

        CurriculumPlanResponse expired = expireCurriculumPlanUseCase.expire(planId,
                new ExpireCurriculumPlanRequest(LocalDate.of(2027, 12, 31), LocalDate.of(2030, 12, 31)));

        assertThat(expired.status()).isEqualTo(CurriculumPlanStatus.EXPIRED);
        assertThat(expired.graduationDeadline()).isEqualTo(LocalDate.of(2030, 12, 31));
        assertThatThrownBy(() -> activateCurriculumPlanUseCase.activate(planId))
                .isInstanceOf(InvalidCurriculumPlanTransitionException.class);
    }

    @Test
    void copyingAPlanClonesCorequisitesWithTheirOwnRowsPointingToTheCopies() {
        Program program = persistProgram("Tecnicatura Correquisitos");
        ProgramSubject first = addSubject(program, persistSubject("Laboratorio"), 1);
        ProgramSubject second = addSubject(program, persistSubject("Teoría"), 1);
        programSubjectService.addCorequisite(first.getId(), new AddCorequisiteRequest(second.getId()));
        entityManager.flush();

        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(),
                draftRequest(first.getPlan().getId()));
        entityManager.flush();

        List<ProgramSubject> copies = programSubjectRepository.findByPlanIdOrderById(draft.id());
        List<Long> copyIds = copies.stream().map(ProgramSubject::getId).toList();
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("select id, program_subject_id, corequisite_program_subject_id "
                + "from program_subject_corequisite where program_subject_id in (select id from program_subject where plan_id = ?)", draft.id());
        assertThat(rows).hasSize(1);
        assertThat(copyIds).contains(((Number) rows.get(0).get("program_subject_id")).longValue(),
                ((Number) rows.get(0).get("corequisite_program_subject_id")).longValue());
        assertThat(jdbc.queryForObject("select count(*) from program_subject_corequisite", Integer.class)
                - jdbc.queryForObject("select count(*) from program_subject_corequisite c join program_subject ps "
                + "on ps.id = c.program_subject_id where ps.plan_id <> ?", Integer.class, draft.id())).isEqualTo(1);
    }

    private CurriculumPlanResponse expiredPlanOf(String name, LocalDate deadline) {
        Program program = persistProgram(name);
        ProgramSubject entry = addSubject(program, persistSubject("Materia " + name), 1);
        Long planId = entry.getPlan().getId();
        updateCurriculumPlanUseCase.update(planId, new UpdateCurriculumPlanRequest(null, LocalDate.of(2020, 1, 1), null, null, null, null));
        return expireCurriculumPlanUseCase.expire(planId, new ExpireCurriculumPlanRequest(LocalDate.of(2021, 1, 1), deadline));
    }

    @Test
    void expiredPlanRejectsNewStudentsButExistingStudentsContinueUntilTheDeadlineInclusive() {
        LocalDate today = LocalDate.now(clock);
        CurriculumPlanResponse deadlineToday = expiredPlanOf("Vencida hoy", today);
        CurriculumPlanResponse deadlinePassed = expiredPlanOf("Vencida ayer", today.minusDays(1));
        CurriculumPlanResponse noDeadline = expiredPlanOf("Vencida sin límite", null);

        assertThatThrownBy(() -> curriculumPlanFinder.requireOpenForNewStudents(deadlineToday.id()))
                .isInstanceOf(CurriculumPlanNotOpenForNewStudentsException.class);
        assertThat(curriculumPlanFinder.allowsExistingStudentsToContinue(deadlineToday.id())).isTrue();
        assertThat(curriculumPlanFinder.allowsExistingStudentsToContinue(deadlinePassed.id())).isFalse();
        assertThat(curriculumPlanFinder.allowsExistingStudentsToContinue(noDeadline.id())).isTrue();
    }

    @Test
    void activatingADraftExpiresThePreviousActivePlanAndKeepsItsStudentsReadable() {
        Program program = persistProgram("Tecnicatura Reemplazo");
        ProgramSubject entry = addSubject(program, persistSubject("Reemplazo"), 1);
        Long previousId = entry.getPlan().getId();
        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(), draftRequest(previousId));

        activateCurriculumPlanUseCase.activate(draft.id());
        entityManager.flush();

        assertThat(curriculumPlanRepository.findActiveByProgramId(program.getId()).orElseThrow().getId()).isEqualTo(draft.id());
        assertThat(curriculumPlanFinder.allowsExistingStudentsToContinue(previousId)).isTrue();
    }
}
