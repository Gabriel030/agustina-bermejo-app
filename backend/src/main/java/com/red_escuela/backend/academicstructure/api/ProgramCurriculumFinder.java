package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectCorequisite;
import com.red_escuela.backend.academicstructure.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectCorequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class ProgramCurriculumFinder {

    private final ProgramSubjectRepository programSubjectRepository;
    private final ProgramSubjectPrerequisiteRepository prerequisiteRepository;
    private final ProgramElectiveGroupRepository electiveGroupRepository;
    private final ProgramSubjectCorequisiteRepository corequisiteRepository;

    public ProgramCurriculumFinder(ProgramSubjectRepository programSubjectRepository,
                                   ProgramSubjectPrerequisiteRepository prerequisiteRepository,
                                   ProgramElectiveGroupRepository electiveGroupRepository,
                                   ProgramSubjectCorequisiteRepository corequisiteRepository) {
        this.programSubjectRepository = programSubjectRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.electiveGroupRepository = electiveGroupRepository;
        this.corequisiteRepository = corequisiteRepository;
    }

    public List<ProgramSubjectView> findProgramSubjects(Long programId) {
        return programSubjectRepository.findByProgramId(programId).stream()
                .map(this::toProgramSubjectView)
                .toList();
    }

    public List<ProgramSubjectView> findProgramSubjectsOfPlan(Long planId) {
        return programSubjectRepository.findByPlanIdOrderById(planId).stream()
                .map(this::toProgramSubjectView)
                .toList();
    }

    public Optional<ProgramSubjectView> findProgramSubjectOfPlan(Long planId, Long subjectId) {
        return programSubjectRepository.findByPlanIdAndSubjectId(planId, subjectId)
                .map(this::toProgramSubjectView);
    }

    public List<ProgramCurriculumEntryView> findActiveCurriculumEntriesOfPlan(Long planId) {
        return programSubjectRepository.findActiveByPlanId(planId).stream()
                .map(this::toCurriculumEntryView)
                .toList();
    }

    public List<ProgramSubjectView> findActiveProgramSubjectsOfPlan(Long planId) {
        return programSubjectRepository.findActiveByPlanId(planId).stream()
                .map(this::toProgramSubjectView)
                .toList();
    }

    public Map<Long, List<PrerequisiteView>> findPrerequisitesOfPlan(Long planId) {
        return prerequisiteRepository.findAllOfPlan(planId).stream()
                .collect(Collectors.groupingBy(prerequisite -> prerequisite.getProgramSubject().getId(),
                        Collectors.mapping(this::toPrerequisiteView, Collectors.toList())));
    }

    public Map<Long, List<ProgramCorequisiteView>> findCorequisitesOfPlan(Long planId) {
        return corequisiteRepository.findAllOfPlan(planId).stream()
                .collect(Collectors.groupingBy(corequisite -> corequisite.getProgramSubject().getId(),
                        Collectors.mapping(this::toCorequisiteView, Collectors.toList())));
    }

    public boolean subjectBelongsToPlan(Long planId, Long subjectId) {
        return programSubjectRepository.findByPlanIdAndSubjectId(planId, subjectId).isPresent();
    }

    public boolean subjectIsActiveInPlan(Long planId, Long subjectId) {
        return programSubjectRepository.existsActiveByPlanIdAndSubjectId(planId, subjectId);
    }

    public List<PrerequisiteView> findPrerequisites(Long programSubjectId) {
        return prerequisiteRepository.findByProgramSubjectId(programSubjectId).stream()
                .map(this::toPrerequisiteView)
                .toList();
    }

    public List<ProgramCurriculumEntryView> findActiveCurriculumEntries(Long programId) {
        return programSubjectRepository.findActiveByProgramId(programId).stream()
                .map(this::toCurriculumEntryView)
                .toList();
    }

    public List<ElectiveGroupRequirementView> findElectiveGroupRequirements(Long programId) {
        return electiveGroupRepository.findByProgramId(programId).stream()
                .map(this::toElectiveGroupRequirementView)
                .toList();
    }

    private ProgramSubjectView toProgramSubjectView(ProgramSubject entry) {
        return new ProgramSubjectView(entry.getId(), entry.getSubject().getId(), entry.getSubject().getName(),
                entry.getCredits(), entry.getCurricularYear(), entry.getCurricularTerm(),
                entry.getElectiveGroup() == null ? null : entry.getElectiveGroup().getId());
    }

    private ProgramCurriculumEntryView toCurriculumEntryView(ProgramSubject entry) {
        return new ProgramCurriculumEntryView(entry.getId(), entry.getSubject().getId(), entry.getSubject().getCode(),
                entry.getSubject().getName(), entry.getCredits(), entry.getCurricularYear(), entry.getCurricularTerm(),
                entry.getCategory(), entry.getElectiveGroup() == null ? null : entry.getElectiveGroup().getId());
    }

    private ProgramCorequisiteView toCorequisiteView(ProgramSubjectCorequisite corequisite) {
        ProgramSubject target = corequisite.getCorequisiteProgramSubject();
        return new ProgramCorequisiteView(target.getId(), target.getSubject().getId(), target.getSubject().getCode(),
                target.getSubject().getName());
    }

    private PrerequisiteView toPrerequisiteView(ProgramSubjectPrerequisite prerequisite) {
        var subject = prerequisite.getPrerequisiteProgramSubject().getSubject();
        return new PrerequisiteView(subject.getId(), subject.getName(), prerequisite.getRequirementType(),
                subject.getCode(), prerequisite.getMinGrade());
    }

    private ElectiveGroupRequirementView toElectiveGroupRequirementView(ProgramElectiveGroup group) {
        return new ElectiveGroupRequirementView(group.getElectiveGroup().getId(), group.getElectiveGroup().getName(),
                group.getMinRequired(), group.getMaxAllowed());
    }
}
