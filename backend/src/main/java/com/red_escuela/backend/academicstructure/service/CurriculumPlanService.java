package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.dto.CreateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.CreateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.CurriculumPlanResponse;
import com.red_escuela.backend.academicstructure.dto.ExpireCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramSubjectResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateCurriculumPlanRequest;
import com.red_escuela.backend.academicstructure.entity.CurriculumPlan;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.domain.CurriculumPlanNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CurriculumPlanRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectCorequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CurriculumPlanService {

    private final ProgramRepository programRepository;
    private final CurriculumPlanRepository curriculumPlanRepository;
    private final ProgramSubjectRepository programSubjectRepository;
    private final ProgramSubjectPrerequisiteRepository prerequisiteRepository;
    private final ProgramSubjectCorequisiteRepository corequisiteRepository;
    private final CurriculumPlanEditPolicy editPolicy;
    private final ProgramSubjectService programSubjectService;

    public CurriculumPlanService(ProgramRepository programRepository,
                                 CurriculumPlanRepository curriculumPlanRepository,
                                 ProgramSubjectRepository programSubjectRepository,
                                 ProgramSubjectPrerequisiteRepository prerequisiteRepository,
                                 ProgramSubjectCorequisiteRepository corequisiteRepository,
                                 CurriculumPlanEditPolicy editPolicy,
                                 ProgramSubjectService programSubjectService) {
        this.programRepository = programRepository;
        this.curriculumPlanRepository = curriculumPlanRepository;
        this.programSubjectRepository = programSubjectRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.corequisiteRepository = corequisiteRepository;
        this.editPolicy = editPolicy;
        this.programSubjectService = programSubjectService;
    }

    @Transactional
    public CurriculumPlanResponse createDraftVersion(Long programId, CreateCurriculumPlanRequest request) {
        Program program = programRepository.findById(programId).orElseThrow(() -> new ProgramNotFoundException(programId));
        Optional<CurriculumPlan> source = Optional.ofNullable(request.copyFromPlanId())
                .map(id -> curriculumPlanRepository.findById(id).orElseThrow(() -> new CurriculumPlanNotFoundException(id)));
        source.ifPresent(plan -> requireSameProgram(plan, programId));
        CurriculumPlan draft = curriculumPlanRepository.save(CurriculumPlan.draftVersionOf(
                program,
                curriculumPlanRepository.findLastVersionNumber(programId) + 1,
                request.name(),
                request.validFrom(),
                Optional.ofNullable(request.totalYears()).or(() -> source.map(CurriculumPlan::getTotalYears)).orElse(null),
                Optional.ofNullable(request.totalCreditsRequired()).or(() -> source.map(CurriculumPlan::getTotalCreditsRequired)).orElse(null),
                Optional.ofNullable(request.totalHoursRequired()).or(() -> source.map(CurriculumPlan::getTotalHoursRequired)).orElse(null)));
        source.ifPresent(plan -> copyEntries(plan, draft));
        return CurriculumPlanResponse.from(draft);
    }

    private void requireSameProgram(CurriculumPlan source, Long programId) {
        if (!source.getProgram().getId().equals(programId)) {
            throw new CurriculumPlanNotFoundException(source.getId());
        }
    }

    private void copyEntries(CurriculumPlan source, CurriculumPlan draft) {
        Map<Long, ProgramSubject> copiesBySourceEntryId = new HashMap<>();
        List<ProgramSubject> sourceEntries = programSubjectRepository.findByPlanIdOrderById(source.getId());
        for (ProgramSubject entry : sourceEntries) {
            ProgramSubject copy = new ProgramSubject();
            copy.setProgram(draft.getProgram());
            copy.setPlan(draft);
            copy.setSubject(entry.getSubject());
            copy.setCredits(entry.getCredits());
            copy.setElectiveGroup(entry.getElectiveGroup());
            copy.setCurricularYear(entry.getCurricularYear());
            copy.setCurricularTerm(entry.getCurricularTerm());
            copy.setSortOrder(entry.getSortOrder());
            copy.setStatus(entry.getStatus());
            copy.setSubjectRecurrence(entry.getSubjectRecurrence());
            copy.setCategory(entry.getCategory());
            copiesBySourceEntryId.put(entry.getId(), programSubjectRepository.save(copy));
        }
        for (ProgramSubject entry : sourceEntries) {
            for (ProgramSubjectPrerequisite prerequisite : prerequisiteRepository.findByProgramSubjectId(entry.getId())) {
                ProgramSubjectPrerequisite copy = new ProgramSubjectPrerequisite();
                copy.setProgramSubject(copiesBySourceEntryId.get(entry.getId()));
                copy.setPrerequisiteProgramSubject(copiesBySourceEntryId.get(prerequisite.getPrerequisiteProgramSubject().getId()));
                copy.setRequirementType(prerequisite.getRequirementType());
                copy.setMinGrade(prerequisite.getMinGrade());
                copy.setNotes(prerequisite.getNotes());
                prerequisiteRepository.save(copy);
            }
            for (ProgramSubjectCorequisite corequisite : corequisiteRepository.findByProgramSubjectId(entry.getId())) {
                ProgramSubjectCorequisite copy = new ProgramSubjectCorequisite();
                copy.setProgramSubject(copiesBySourceEntryId.get(entry.getId()));
                copy.setCorequisiteProgramSubject(copiesBySourceEntryId.get(corequisite.getCorequisiteProgramSubject().getId()));
                corequisiteRepository.save(copy);
            }
        }
    }

    @Transactional
    public CurriculumPlanResponse update(Long planId, UpdateCurriculumPlanRequest request) {
        CurriculumPlan plan = findOrThrow(planId);
        if (request.changesTotals()) {
            editPolicy.requireStructureEditable(plan);
            plan.changeTotals(request.totalYears(), request.totalCreditsRequired(), request.totalHoursRequired());
            adoptTotalsIfCurrent(plan);
        }
        Optional.ofNullable(request.name()).ifPresent(plan::setName);
        plan.reschedule(request.validFrom(), request.graduationDeadline());
        return CurriculumPlanResponse.from(curriculumPlanRepository.save(plan));
    }

    @Transactional
    public CurriculumPlanResponse activate(Long planId) {
        CurriculumPlan plan = findOrThrow(planId);
        plan.activate();
        curriculumPlanRepository.save(plan);
        adoptTotalsIfCurrent(plan);
        return CurriculumPlanResponse.from(plan);
    }

    @Transactional
    public CurriculumPlanResponse expire(Long planId, ExpireCurriculumPlanRequest request) {
        CurriculumPlan plan = findOrThrow(planId);
        plan.expire(request.expiresOn(), request.graduationDeadline());
        return CurriculumPlanResponse.from(curriculumPlanRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public CurriculumPlanResponse get(Long planId) {
        return CurriculumPlanResponse.from(findOrThrow(planId));
    }

    @Transactional(readOnly = true)
    public List<CurriculumPlanResponse> listByProgram(Long programId) {
        return curriculumPlanRepository.findByProgramIdOrderByVersionNumberAsc(programId).stream()
                .map(CurriculumPlanResponse::from).toList();
    }

    @Transactional
    public ProgramSubjectResponse addSubject(Long planId, CreateProgramSubjectRequest request) {
        return ProgramSubjectResponse.from(programSubjectService.addSubjectToPlan(planId, request));
    }

    @Transactional(readOnly = true)
    public List<ProgramSubjectResponse> listSubjects(Long planId) {
        return programSubjectService.listByPlan(planId).stream().map(ProgramSubjectResponse::from).toList();
    }

    private void adoptTotalsIfCurrent(CurriculumPlan plan) {
        curriculumPlanRepository.findCurrentByProgramId(plan.getProgram().getId())
                .filter(current -> current.getId().equals(plan.getId()))
                .ifPresent(current -> plan.getProgram().adoptCurriculumTotals(plan));
    }

    private CurriculumPlan findOrThrow(Long planId) {
        return curriculumPlanRepository.findById(planId).orElseThrow(() -> new CurriculumPlanNotFoundException(planId));
    }
}
