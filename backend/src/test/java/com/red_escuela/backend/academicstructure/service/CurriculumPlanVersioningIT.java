package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
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
        CurriculumPlanResponse draft = createCurriculumPlanUseCase.createDraftVersion(program.getId(), draftRequest(null));

        updateCurriculumPlanUseCase.update(draft.id(), new UpdateCurriculumPlanRequest(null, null, null, 4, 150, null));
        activateCurriculumPlanUseCase.activate(draft.id());
        entityManager.flush();
        entityManager.clear();

        CurriculumPlan current = curriculumPlanRepository.findCurrentByProgramId(program.getId()).orElseThrow();
        assertThat(current.getId()).isEqualTo(draft.id());
        assertThat(current.getTotalYears()).isEqualTo(4);
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
    void subjectInsertedWithoutPlanGetsTheProgramActivePlanFromTheDatabase() {
        Program program = persistProgram("Tecnicatura Trigger");
        Subject subject = persistSubject("Historia");

        Long entryId = jdbc.queryForObject("insert into program_subject (program_id, subject_id, credits, category, status) "
                + "values (?, ?, 5, 'MANDATORY', 'ACTIVE') returning id", Long.class, program.getId(), subject.getId());

        List<CurriculumPlan> plans = curriculumPlanRepository.findByProgramIdOrderByVersionNumberAsc(program.getId());
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).getStatus()).isEqualTo(CurriculumPlanStatus.ACTIVE);
        assertThat(plans.get(0).getTotalCreditsRequired()).isEqualTo(100);
        assertThat(jdbc.queryForObject("select plan_id from program_subject where id = ?", Long.class, entryId))
                .isEqualTo(plans.get(0).getId());
    }
}
