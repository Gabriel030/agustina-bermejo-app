package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumEntryView;
import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.api.ProgramSubjectView;
import com.red_escuela.backend.enrollment.dto.EquivalenceActorResponse;
import com.red_escuela.backend.enrollment.dto.EquivalenceDocumentResponse;
import com.red_escuela.backend.enrollment.dto.OwnSubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceResponse;
import com.red_escuela.backend.enrollment.dto.SubjectEquivalenceStatusFilter;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.SubjectEquivalenceNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class SubjectEquivalenceReader {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectEquivalenceRepository subjectEquivalenceRepository;
    private final StudentDocumentRepository studentDocumentRepository;
    private final ProgramCurriculumFinder programCurriculumFinder;
    private final PersonNameFinder personNameFinder;

    public SubjectEquivalenceReader(ProgramEnrollmentRepository programEnrollmentRepository,
                                    SubjectEquivalenceRepository subjectEquivalenceRepository,
                                    StudentDocumentRepository studentDocumentRepository,
                                    ProgramCurriculumFinder programCurriculumFinder, PersonNameFinder personNameFinder) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.subjectEquivalenceRepository = subjectEquivalenceRepository;
        this.studentDocumentRepository = studentDocumentRepository;
        this.programCurriculumFinder = programCurriculumFinder;
        this.personNameFinder = personNameFinder;
    }

    public List<SubjectEquivalenceResponse> list(Long programEnrollmentId, SubjectEquivalenceStatusFilter filter) {
        ProgramEnrollment enrollment = requireEnrollment(programEnrollmentId);
        return toStaffResponses(enrollment, findByFilter(programEnrollmentId, filter));
    }

    public SubjectEquivalenceResponse get(Long programEnrollmentId, Long equivalenceId) {
        ProgramEnrollment enrollment = requireEnrollment(programEnrollmentId);
        SubjectEquivalence equivalence = subjectEquivalenceRepository.findById(equivalenceId)
                .filter(found -> found.getProgramEnrollment().getId().equals(programEnrollmentId))
                .orElseThrow(() -> new SubjectEquivalenceNotFoundException(equivalenceId));
        return toStaffResponses(enrollment, List.of(equivalence)).get(0);
    }

    public List<OwnSubjectEquivalenceResponse> listOwn(Long studentId, Long programEnrollmentId) {
        ProgramEnrollment enrollment = requireEnrollment(programEnrollmentId);
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new ProgramEnrollmentNotFoundException(programEnrollmentId);
        }
        List<SubjectEquivalence> active = subjectEquivalenceRepository
                .findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(programEnrollmentId, SubjectEquivalenceStatus.ACTIVE);
        SubjectLabels labels = labelsOf(enrollment);
        Map<Long, StudentDocument> documents = documentsOf(active);
        return active.stream().map(equivalence -> toOwnResponse(equivalence, labels, documents)).toList();
    }

    private ProgramEnrollment requireEnrollment(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
    }

    private List<SubjectEquivalence> findByFilter(Long programEnrollmentId, SubjectEquivalenceStatusFilter filter) {
        return switch (filter) {
            case ALL -> subjectEquivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(programEnrollmentId);
            case ACTIVE -> subjectEquivalenceRepository.findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(
                    programEnrollmentId, SubjectEquivalenceStatus.ACTIVE);
            case VOIDED -> subjectEquivalenceRepository.findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(
                    programEnrollmentId, SubjectEquivalenceStatus.VOIDED);
        };
    }

    private List<SubjectEquivalenceResponse> toStaffResponses(ProgramEnrollment enrollment, List<SubjectEquivalence> equivalences) {
        SubjectLabels labels = labelsOf(enrollment);
        Map<Long, StudentDocument> documents = documentsOf(equivalences);
        Set<Long> actorIds = new HashSet<>();
        equivalences.forEach(equivalence -> {
            actorIds.add(equivalence.getRecordedById());
            if (equivalence.getVoidedById() != null) {
                actorIds.add(equivalence.getVoidedById());
            }
        });
        Map<Long, String> names = personNameFinder.findFullNamesByPersonId(actorIds);
        return equivalences.stream().map(equivalence -> toStaffResponse(equivalence, labels, documents, names)).toList();
    }

    private SubjectEquivalenceResponse toStaffResponse(SubjectEquivalence equivalence, SubjectLabels labels,
                                                       Map<Long, StudentDocument> documents, Map<Long, String> names) {
        SubjectLabel label = labels.of(equivalence.getSubjectId());
        return new SubjectEquivalenceResponse(equivalence.getId(), equivalence.getSubjectId(), label.code(), label.name(),
                label.credits(), equivalence.getOriginInstitution(), equivalence.getOriginSubjectName(),
                equivalence.getOriginScore(), equivalence.getRecognizedOn(),
                equivalence.getResolutionNumber(), documentOf(equivalence, documents), equivalence.getReason(),
                equivalence.getStatus().name(), actorOf(equivalence.getRecordedById(), names), equivalence.getRecordedAt(),
                actorOf(equivalence.getVoidedById(), names), equivalence.getVoidedAt(), equivalence.getVoidReason(),
                label.inCurrentPlan());
    }

    private OwnSubjectEquivalenceResponse toOwnResponse(SubjectEquivalence equivalence, SubjectLabels labels,
                                                        Map<Long, StudentDocument> documents) {
        SubjectLabel label = labels.of(equivalence.getSubjectId());
        return new OwnSubjectEquivalenceResponse(equivalence.getId(), equivalence.getSubjectId(), label.code(), label.name(),
                label.credits(), equivalence.getOriginInstitution(), equivalence.getOriginSubjectName(),
                equivalence.getOriginScore(), equivalence.getRecognizedOn(),
                equivalence.getResolutionNumber(), documentOf(equivalence, documents), equivalence.getStatus().name(),
                label.inCurrentPlan());
    }

    private EquivalenceActorResponse actorOf(Long personId, Map<Long, String> names) {
        return personId == null ? null : new EquivalenceActorResponse(personId, names.get(personId));
    }

    private EquivalenceDocumentResponse documentOf(SubjectEquivalence equivalence, Map<Long, StudentDocument> documents) {
        StudentDocument document = equivalence.getStudentDocumentId() == null ? null : documents.get(equivalence.getStudentDocumentId());
        return document == null ? null : new EquivalenceDocumentResponse(document.getId(), document.getType().name());
    }

    private Map<Long, StudentDocument> documentsOf(List<SubjectEquivalence> equivalences) {
        List<Long> documentIds = equivalences.stream().map(SubjectEquivalence::getStudentDocumentId)
                .filter(Objects::nonNull).distinct().toList();
        return studentDocumentRepository.findAllById(documentIds).stream()
                .collect(Collectors.toMap(StudentDocument::getId, Function.identity()));
    }

    private SubjectLabels labelsOf(ProgramEnrollment enrollment) {
        Long programId = enrollment.getProgram().getId();
        Map<Long, ProgramCurriculumEntryView> activeEntries = programCurriculumFinder.findActiveCurriculumEntries(programId).stream()
                .collect(Collectors.toMap(ProgramCurriculumEntryView::subjectId, Function.identity(), (first, second) -> first));
        Map<Long, ProgramSubjectView> planEntries = programCurriculumFinder.findProgramSubjects(programId).stream()
                .collect(Collectors.toMap(ProgramSubjectView::subjectId, Function.identity(), (first, second) -> first));
        return new SubjectLabels(activeEntries, planEntries);
    }

    private record SubjectLabel(String code, String name, Integer credits, boolean inCurrentPlan) {
    }

    private record SubjectLabels(Map<Long, ProgramCurriculumEntryView> activeEntries, Map<Long, ProgramSubjectView> planEntries) {
        SubjectLabel of(Long subjectId) {
            ProgramCurriculumEntryView active = activeEntries.get(subjectId);
            if (active != null) {
                return new SubjectLabel(active.subjectCode(), active.subjectName(), active.credits(), true);
            }
            ProgramSubjectView inactive = planEntries.get(subjectId);
            return inactive == null
                    ? new SubjectLabel(null, null, null, false)
                    : new SubjectLabel(null, inactive.subjectName(), null, false);
        }
    }
}
