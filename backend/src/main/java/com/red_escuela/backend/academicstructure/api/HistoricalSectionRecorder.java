package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.HistoricalReferenceMissingException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.InstructorRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.identity.api.HistoricalPersonRecorder;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.shared.persistence.InClauseChunks;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalSectionRecorder {
    private final SubjectSectionRepository sectionRepository;
    private final SubjectSectionInstructorRepository sectionInstructorRepository;
    private final SubjectRepository subjectRepository;
    private final TermRepository termRepository;
    private final AcademicOfferingCycleRepository cycleRepository;
    private final InstructorRepository instructorRepository;
    private final ProgramRepository programRepository;
    private final HistoricalPersonRecorder personRecorder;

    public HistoricalSectionRecorder(SubjectSectionRepository sectionRepository,
            SubjectSectionInstructorRepository sectionInstructorRepository, SubjectRepository subjectRepository,
            TermRepository termRepository, AcademicOfferingCycleRepository cycleRepository,
            InstructorRepository instructorRepository, ProgramRepository programRepository,
            HistoricalPersonRecorder personRecorder) {
        this.sectionRepository = sectionRepository;
        this.sectionInstructorRepository = sectionInstructorRepository;
        this.subjectRepository = subjectRepository;
        this.termRepository = termRepository;
        this.cycleRepository = cycleRepository;
        this.instructorRepository = instructorRepository;
        this.programRepository = programRepository;
        this.personRecorder = personRecorder;
    }

    @Transactional(readOnly = true)
    public boolean sectionCodeExists(String code) {
        return sectionRepository.existsByCode(code);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findSectionIdByCode(String code) {
        return sectionRepository.findByCode(code).map(SubjectSection::getId);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> findSectionIdsByCodes(Collection<String> codes) {
        Map<String, Long> sectionIds = new HashMap<>();
        for (List<String> chunk : InClauseChunks.of(codes)) {
            sectionRepository.findNamedIdsByCodeIn(chunk).forEach(row -> sectionIds.putIfAbsent(row.name(), row.id()));
        }
        return sectionIds;
    }

    @Transactional(readOnly = true)
    public boolean instructorExists(String dni) {
        return findInstructor(dni).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean personWithoutInstructorProfile(String dni) {
        return personRecorder.documentExists(DocumentType.DNI, dni) && findInstructor(dni).isEmpty();
    }

    @Transactional
    public Long recordSection(HistoricalSection historical) {
        Subject subject = subjectRepository.findByCode(historical.subjectCode())
                .orElseThrow(() -> new HistoricalReferenceMissingException(historical.subjectCode()));
        Term term = termRepository.findFirstByYearAndTypeOrderByIdAsc(historical.year(), historical.termType())
                .orElseThrow(() -> new HistoricalReferenceMissingException(historical.year() + " " + historical.termType()));
        Instructor instructor = historical.instructorDni().isEmpty() ? null : findInstructor(historical.instructorDni())
                .orElseThrow(() -> new HistoricalReferenceMissingException(historical.instructorDni()));
        SubjectSection section = new SubjectSection();
        section.setCode(historical.code());
        section.setName(historical.name());
        section.setSubject(subject);
        section.setTerm(term);
        section.setShift(historical.shift());
        section.setStatus(StatusType.INACTIVE);
        section.setCreatedAt(ApplicationTime.now());
        section.setAcademicOfferingCycle(findCycle(term, historical.programName()));
        SubjectSection saved = sectionRepository.save(section);
        if (instructor != null) {
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(saved);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            assignment.setStartAt(term.getStartDate());
            assignment.setEndAt(term.getEndDate());
            sectionInstructorRepository.save(assignment);
        }
        return saved.getId();
    }

    @Transactional
    public void discardSection(Long sectionId) {
        sectionInstructorRepository.deleteAll(sectionInstructorRepository.findBySectionId(sectionId));
        sectionInstructorRepository.flush();
        sectionRepository.deleteById(sectionId);
        sectionRepository.flush();
    }

    @Transactional(readOnly = true)
    public Optional<Long> findLeadInstructorId(Long sectionId) {
        return sectionInstructorRepository.findBySectionId(sectionId).stream()
                .filter(assignment -> assignment.getRole() == InstructorRole.LEAD)
                .map(assignment -> assignment.getInstructor().getId()).findFirst();
    }

    @Transactional(readOnly = true)
    public boolean sectionHasLeadInstructor(String code) {
        return findSectionIdByCode(code).flatMap(this::findLeadInstructorId).isPresent();
    }

    private AcademicOfferingCycle findCycle(Term term, String programName) {
        if (programName.isEmpty()) {
            return cycleRepository.findFirstByTermIdOrderByIdAsc(term.getId()).orElse(null);
        }
        return programRepository.findFirstByName(programName)
                .flatMap(program -> cycleRepository.findFirstByTermIdAndProgramIdOrderByIdAsc(term.getId(), program.getId()))
                .orElseThrow(() -> new HistoricalReferenceMissingException(programName));
    }

    private Optional<Instructor> findInstructor(String dni) {
        return personRecorder.findIdByDocument(DocumentType.DNI, dni).flatMap(instructorRepository::findByPerson_Id);
    }
}
