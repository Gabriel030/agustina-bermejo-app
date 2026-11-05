package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.ProgramElectiveGroup;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.repository.ProgramElectiveGroupRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class ProgramCurriculumFinder {

    private final ProgramSubjectRepository programSubjectRepository;
    private final ProgramSubjectPrerequisiteRepository prerequisiteRepository;
    private final ProgramElectiveGroupRepository electiveGroupRepository;

    public ProgramCurriculumFinder(ProgramSubjectRepository programSubjectRepository,
                                   ProgramSubjectPrerequisiteRepository prerequisiteRepository,
                                   ProgramElectiveGroupRepository electiveGroupRepository) {
        this.programSubjectRepository = programSubjectRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.electiveGroupRepository = electiveGroupRepository;
    }

    public List<ProgramSubjectView> findProgramSubjects(Long programId) {
        return programSubjectRepository.findByProgramId(programId).stream()
                .map(this::toProgramSubjectView)
                .toList();
    }

    public Optional<ProgramSubjectView> findProgramSubject(Long programId, Long subjectId) {
        return programSubjectRepository.findByProgramIdAndSubjectId(programId, subjectId)
                .map(this::toProgramSubjectView);
    }

    public boolean subjectBelongsToProgram(Long programId, Long subjectId) {
        return programSubjectRepository.findByProgramIdAndSubjectId(programId, subjectId).isPresent();
    }

    public List<PrerequisiteView> findPrerequisites(Long programSubjectId) {
        return prerequisiteRepository.findByProgramSubjectId(programSubjectId).stream()
                .map(this::toPrerequisiteView)
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

    private PrerequisiteView toPrerequisiteView(ProgramSubjectPrerequisite prerequisite) {
        var subject = prerequisite.getPrerequisiteProgramSubject().getSubject();
        return new PrerequisiteView(subject.getId(), subject.getName(), prerequisite.getRequirementType());
    }

    private ElectiveGroupRequirementView toElectiveGroupRequirementView(ProgramElectiveGroup group) {
        return new ElectiveGroupRequirementView(group.getElectiveGroup().getId(), group.getElectiveGroup().getName(),
                group.getMinRequired(), group.getMaxAllowed());
    }
}
