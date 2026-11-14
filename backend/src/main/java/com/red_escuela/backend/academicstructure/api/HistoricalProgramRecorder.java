package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.exception.HistoricalReferenceMissingException;
import com.red_escuela.backend.academicstructure.exception.OfferingListPriceNotPositiveException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectPrerequisiteRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramSubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.exception.HistoricalDiscardBlockedException;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceCatalog;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalProgramRecorder {
    private final ProgramRepository programRepository;
    private final SubjectRepository subjectRepository;
    private final ProgramSubjectRepository programSubjectRepository;
    private final ProgramSubjectPrerequisiteRepository prerequisiteRepository;
    private final OfferingPriceCatalog offeringPriceCatalog;
    private final CurrentActorFinder currentActorFinder;

    public HistoricalProgramRecorder(ProgramRepository programRepository, SubjectRepository subjectRepository,
            ProgramSubjectRepository programSubjectRepository,
            ProgramSubjectPrerequisiteRepository prerequisiteRepository, OfferingPriceCatalog offeringPriceCatalog,
            CurrentActorFinder currentActorFinder) {
        this.programRepository = programRepository;
        this.subjectRepository = subjectRepository;
        this.programSubjectRepository = programSubjectRepository;
        this.prerequisiteRepository = prerequisiteRepository;
        this.offeringPriceCatalog = offeringPriceCatalog;
        this.currentActorFinder = currentActorFinder;
    }

    @Transactional(readOnly = true)
    public boolean programExists(String name) {
        return programRepository.findFirstByName(name).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<Long> findProgramIdByName(String name) {
        return programRepository.findFirstByName(name).map(Program::getId);
    }

    @Transactional(readOnly = true)
    public boolean subjectCodeExists(String code) {
        return subjectRepository.findByCode(code).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean subjectNameExists(String name) {
        return subjectRepository.findByName(name).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean planEntryExists(String programName, String subjectCode) {
        return findPlanEntry(programName, subjectCode).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean prerequisiteExists(String programName, String subjectCode, String prerequisiteCode) {
        Optional<ProgramSubject> subject = findPlanEntry(programName, subjectCode);
        Optional<ProgramSubject> prerequisite = findPlanEntry(programName, prerequisiteCode);
        return subject.isPresent() && prerequisite.isPresent()
                && prerequisiteRepository.existsByProgramSubjectIdAndPrerequisiteProgramSubjectId(subject.get().getId(),
                        prerequisite.get().getId());
    }

    @Transactional
    public Long recordProgram(HistoricalProgram historical) {
        requirePositive(historical.tuition());
        requirePositive(historical.enrollmentFee());
        Program program = programRepository.save(Program.builder().name(historical.name()).level(historical.level())
                .totalYears(historical.totalYears()).totalCreditsRequired(historical.totalCreditsRequired())
                .totalHoursRequired(historical.totalHours()).teachingMode(historical.teachingMode())
                .graduationProfile(historical.graduationProfile())
                .status(historical.active() ? StatusType.ACTIVE : StatusType.INACTIVE)
                .currency(historical.tuition().currency()).build());
        Long actorId = currentActorFinder.currentPersonId();
        OfferingReference offering = OfferingReference.ofProgram(program.getId());
        offeringPriceCatalog.registerInitialOfferingPrice(offering, OfferingFee.MONTHLY_INSTALLMENT, historical.tuition(), actorId);
        offeringPriceCatalog.registerInitialOfferingPrice(offering, OfferingFee.ENROLLMENT_FEE, historical.enrollmentFee(), actorId);
        return program.getId();
    }

    @Transactional
    public Long recordSubject(HistoricalSubject historical) {
        Subject subject = Subject.builder().code(historical.code()).name(historical.name())
                .weeklyHours(historical.weeklyHours()).gradeMode(historical.gradeMode())
                .status(historical.active() ? SubjectStatus.ACTIVE : SubjectStatus.INACTIVE).build();
        subject.setMaxFinalExamAttempts(historical.maxFinalExamAttempts());
        return subjectRepository.save(subject).getId();
    }

    @Transactional
    public Long recordPlanEntry(HistoricalPlanEntry entry) {
        Program program = programRepository.findFirstByName(entry.programName())
                .orElseThrow(() -> new HistoricalReferenceMissingException(entry.programName()));
        Subject subject = subjectRepository.findByCode(entry.subjectCode())
                .orElseThrow(() -> new HistoricalReferenceMissingException(entry.subjectCode()));
        program.requireCurricularYearWithinDuration(entry.curricularYear());
        ProgramSubject planEntry = new ProgramSubject();
        planEntry.setProgram(program);
        planEntry.setSubject(subject);
        planEntry.setCredits(entry.credits());
        planEntry.setCurricularYear(entry.curricularYear());
        planEntry.setCurricularTerm(entry.curricularTerm());
        planEntry.setSortOrder(entry.sortOrder());
        planEntry.setSubjectRecurrence(entry.recurrence());
        planEntry.setCategory(entry.category());
        planEntry.setStatus(StatusType.ACTIVE);
        return programSubjectRepository.save(planEntry).getId();
    }

    @Transactional
    public Long recordPrerequisite(HistoricalPrerequisite historical) {
        ProgramSubject subject = requirePlanEntry(historical.programName(), historical.subjectCode());
        ProgramSubject prerequisite = requirePlanEntry(historical.programName(), historical.prerequisiteCode());
        ProgramSubjectPrerequisite link = new ProgramSubjectPrerequisite();
        link.setProgramSubject(subject);
        link.setPrerequisiteProgramSubject(prerequisite);
        link.setRequirementType(historical.requirementType());
        link.setMinGrade(Grade.ofNullable(historical.minGrade()));
        return prerequisiteRepository.save(link).getId();
    }

    @Transactional
    public void discardProgram(Long programId) {
        throw new HistoricalDiscardBlockedException(
                "Las carreras importadas tienen precios cargados y el historial de precios no se puede borrar");
    }

    @Transactional
    public void discardSubject(Long subjectId) {
        subjectRepository.deleteById(subjectId);
        subjectRepository.flush();
    }

    @Transactional
    public void discardPlanEntry(Long planEntryId) {
        programSubjectRepository.deleteById(planEntryId);
        programSubjectRepository.flush();
    }

    @Transactional
    public void discardPrerequisite(Long prerequisiteId) {
        prerequisiteRepository.deleteById(prerequisiteId);
        prerequisiteRepository.flush();
    }

    private Optional<ProgramSubject> findPlanEntry(String programName, String subjectCode) {
        Optional<Program> program = programRepository.findFirstByName(programName);
        Optional<Subject> subject = subjectRepository.findByCode(subjectCode);
        if (program.isEmpty() || subject.isEmpty()) {
            return Optional.empty();
        }
        return programSubjectRepository.findByProgramIdAndSubjectId(program.get().getId(), subject.get().getId());
    }

    private ProgramSubject requirePlanEntry(String programName, String subjectCode) {
        return findPlanEntry(programName, subjectCode)
                .orElseThrow(() -> new HistoricalReferenceMissingException(programName + " / " + subjectCode));
    }

    private static void requirePositive(Money price) {
        if (price == null || price.amount().signum() <= 0) {
            throw new OfferingListPriceNotPositiveException("el precio de lista");
        }
    }
}
