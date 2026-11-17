package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.HistoricalEnrollmentReferenceMissingException;
import com.red_escuela.backend.enrollment.exception.HistoricalFinalDateBeforeEnrollmentException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.identity.api.HistoricalPersonRecorder;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import com.red_escuela.backend.shared.persistence.InClauseChunks;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalEnrollmentRecorder {
    private static final LocalTime HISTORICAL_TIME = LocalTime.NOON;

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository sectionEnrollmentRepository;
    private final HistoricalPersonRecorder personRecorder;
    private final StudentFinder studentFinder;
    private final HistoricalProgramRecorder programRecorder;
    private final EntityManager entityManager;

    public HistoricalEnrollmentRecorder(ProgramEnrollmentRepository programEnrollmentRepository,
            SubjectSectionEnrollmentRepository sectionEnrollmentRepository, HistoricalPersonRecorder personRecorder,
            StudentFinder studentFinder, HistoricalProgramRecorder programRecorder, EntityManager entityManager) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.sectionEnrollmentRepository = sectionEnrollmentRepository;
        this.personRecorder = personRecorder;
        this.studentFinder = studentFinder;
        this.programRecorder = programRecorder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public boolean programEnrollmentExists(DocumentType type, String number, String programName) {
        return findProgramEnrollment(type, number, programName).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> findProgramEnrollmentDate(DocumentType type, String number, String programName) {
        return findProgramEnrollment(type, number, programName)
                .map(enrollment -> enrollment.getEnrollmentDate().toLocalDate());
    }

    @Transactional(readOnly = true)
    public Long requireCompletedProgramEnrollmentId(DocumentType type, String number, String programName,
            LocalDate completionDate) {
        return findCompletedProgramEnrollmentId(type, number, programName, completionDate)
                .orElseThrow(() -> new HistoricalEnrollmentReferenceMissingException(programName));
    }

    @Transactional(readOnly = true)
    public Optional<Long> findProgramEnrollmentId(DocumentType type, String number, String programName) {
        return findProgramEnrollment(type, number, programName).map(ProgramEnrollment::getId);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findCompletedProgramEnrollmentId(DocumentType type, String number, String programName,
            LocalDate completionDate) {
        return findProgramEnrollment(type, number, programName)
                .filter(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.COMPLETED)
                .filter(enrollment -> enrollment.getCompletionDate() != null
                        && enrollment.getCompletionDate().toLocalDate().equals(completionDate))
                .map(ProgramEnrollment::getId);
    }

    @Transactional(readOnly = true)
    public boolean sectionEnrollmentExists(DocumentType type, String number, Long sectionId) {
        return findStudentId(type, number)
                .flatMap(studentId -> sectionEnrollmentRepository.findByStudentIdAndSubjectSectionId(studentId, sectionId))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public Map<StudentProgram, LocalDate> findProgramEnrollmentDates(Collection<StudentProgram> pairs) {
        Map<StudentProgram, LocalDate> dates = new HashMap<>();
        findProgramEnrollmentRefs(pairs).forEach((pair, ref) -> dates.put(pair, ref.enrollmentDate()));
        return dates;
    }

    @Transactional(readOnly = true)
    public Map<StudentProgram, ProgramEnrollmentRef> findProgramEnrollmentRefs(Collection<StudentProgram> pairs) {
        Map<StudentProgram, ProgramEnrollmentRef> refs = new HashMap<>();
        for (List<StudentProgram> chunk : InClauseChunks.of(pairs)) {
            Set<StudentProgram> wanted = new HashSet<>(chunk);
            Set<Long> studentIds = new HashSet<>();
            Set<Long> programIds = new HashSet<>();
            chunk.forEach(pair -> {
                studentIds.add(pair.studentId());
                programIds.add(pair.programId());
            });
            programEnrollmentRepository.findDateRowsByStudentIdInAndProgramIdIn(studentIds, programIds).forEach(row -> {
                StudentProgram pair = new StudentProgram(row.studentId(), row.programId());
                if (wanted.contains(pair)) {
                    refs.putIfAbsent(pair, new ProgramEnrollmentRef(row.enrollmentId(), row.enrollmentDate().toLocalDate()));
                }
            });
        }
        return refs;
    }

    @Transactional(readOnly = true)
    public Set<StudentSection> findExistingSectionEnrollments(Collection<StudentSection> pairs) {
        Set<StudentSection> existing = new HashSet<>();
        for (List<StudentSection> chunk : InClauseChunks.of(pairs)) {
            Set<StudentSection> wanted = new HashSet<>(chunk);
            Set<Long> studentIds = new HashSet<>();
            Set<Long> sectionIds = new HashSet<>();
            chunk.forEach(pair -> {
                studentIds.add(pair.studentId());
                sectionIds.add(pair.sectionId());
            });
            sectionEnrollmentRepository.findPairRowsByStudentIdInAndSubjectSectionIdIn(studentIds, sectionIds).forEach(row -> {
                StudentSection pair = new StudentSection(row.studentId(), row.sectionId());
                if (wanted.contains(pair)) {
                    existing.add(pair);
                }
            });
        }
        return existing;
    }

    @Transactional
    public Long recordProgramEnrollment(HistoricalProgramEnrollment historical) {
        Long studentId = requireStudentId(historical.documentType(), historical.documentNumber());
        Long programId = programRecorder.findProgramIdByName(historical.programName())
                .orElseThrow(() -> new HistoricalEnrollmentReferenceMissingException(historical.programName()));
        return recordProgramEnrollment(historical, studentId, programId);
    }

    @Transactional
    public Long recordProgramEnrollment(HistoricalProgramEnrollment historical, Long studentId, Long programId) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(entityManager.getReference(Student.class, studentId));
        enrollment.setProgram(entityManager.getReference(Program.class, programId));
        enrollment.setStatus(historical.status());
        enrollment.setEnrollmentDate(historical.enrollmentDate().atTime(HISTORICAL_TIME));
        enrollment.setEntryYear(historical.entryYear());
        if (historical.completionDate() != null) {
            enrollment.setCompletionDate(historical.completionDate().atTime(HISTORICAL_TIME));
        }
        if (historical.status() == ProgramEnrollmentStatus.WITHDRAWN) {
            enrollment.setWithdrawalEffectiveDate(historical.withdrawalDate() != null ? historical.withdrawalDate()
                    : historical.completionDate());
        }
        return programEnrollmentRepository.save(enrollment).getId();
    }

    @Transactional
    public Long recordSectionEnrollment(HistoricalSectionEnrollment historical) {
        Long studentId = requireStudentId(historical.documentType(), historical.documentNumber());
        ProgramEnrollment programEnrollment = findProgramEnrollment(historical.documentType(),
                historical.documentNumber(), historical.programName())
                .orElseThrow(() -> new HistoricalEnrollmentReferenceMissingException(historical.programName()));
        LocalDate enrollmentDate = programEnrollment.getEnrollmentDate().toLocalDate();
        if (historical.finalDate() != null && historical.finalDate().isBefore(enrollmentDate)) {
            throw new HistoricalFinalDateBeforeEnrollmentException(historical.finalDate(), enrollmentDate);
        }
        SubjectSection section = entityManager.find(SubjectSection.class, historical.sectionId());
        if (section == null) {
            throw new HistoricalEnrollmentReferenceMissingException("comisión " + historical.sectionId());
        }
        return saveSectionEnrollment(historical, studentId, programEnrollment, section, section.getTerm().getStartDate());
    }

    @Transactional
    public Long recordSectionEnrollment(HistoricalSectionEnrollment historical, SectionEnrollmentReferences references) {
        if (historical.finalDate() != null && historical.finalDate().isBefore(references.programEnrollmentDate())) {
            throw new HistoricalFinalDateBeforeEnrollmentException(historical.finalDate(), references.programEnrollmentDate());
        }
        return saveSectionEnrollment(historical, references.studentId(),
                entityManager.getReference(ProgramEnrollment.class, references.programEnrollmentId()),
                entityManager.getReference(SubjectSection.class, historical.sectionId()), references.sectionTermStart());
    }

    private Long saveSectionEnrollment(HistoricalSectionEnrollment historical, Long studentId,
            ProgramEnrollment programEnrollment, SubjectSection section, LocalDateTime enrolledAt) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(entityManager.getReference(Student.class, studentId));
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(historical.status());
        enrollment.setAttemptNumber(historical.attemptNumber());
        enrollment.setEnrolledAt(enrolledAt);
        enrollment.setFinalScore(Grade.ofNullable(historical.finalScore()));
        enrollment.setFinalGradeType(historical.finalGradeType());
        enrollment.setFinalScoreDate(historical.finalDate());
        return sectionEnrollmentRepository.save(enrollment).getId();
    }

    @Transactional
    public void discardProgramEnrollment(Long enrollmentId) {
        programEnrollmentRepository.deleteById(enrollmentId);
        programEnrollmentRepository.flush();
    }

    @Transactional
    public void discardSectionEnrollment(Long enrollmentId) {
        sectionEnrollmentRepository.deleteById(enrollmentId);
        sectionEnrollmentRepository.flush();
    }

    @Transactional(readOnly = true)
    public Optional<Long> findSectionEnrollmentId(DocumentType type, String number, Long sectionId) {
        return findStudentId(type, number)
                .flatMap(studentId -> sectionEnrollmentRepository.findByStudentIdAndSubjectSectionId(studentId, sectionId))
                .map(SubjectSectionEnrollment::getId);
    }

    private Optional<ProgramEnrollment> findProgramEnrollment(DocumentType type, String number, String programName) {
        Optional<Long> studentId = findStudentId(type, number);
        Optional<Long> programId = programRecorder.findProgramIdByName(programName);
        if (studentId.isEmpty() || programId.isEmpty()) {
            return Optional.empty();
        }
        return programEnrollmentRepository.findFirstByStudentIdAndProgramIdOrderByIdAsc(studentId.get(), programId.get());
    }

    private Optional<Long> findStudentId(DocumentType type, String number) {
        return personRecorder.findIdByDocument(type, number).flatMap(studentFinder::findIdByPersonId);
    }

    private Long requireStudentId(DocumentType type, String number) {
        return findStudentId(type, number).orElseThrow(() -> new HistoricalEnrollmentReferenceMissingException(type + ":" + number));
    }
}
