package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradeEntryRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradesResponse;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.evaluation.exception.ExamBoardCanceledException;
import com.red_escuela.backend.evaluation.exception.ExamBoardClosedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardFinalGradeRowRejectedException;
import com.red_escuela.backend.evaluation.exception.ExamBoardNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationNotFoundException;
import com.red_escuela.backend.evaluation.exception.ExamBoardRegistrationWithdrawnException;
import com.red_escuela.backend.evaluation.exception.InvalidExamBoardFinalGradeEntryException;
import com.red_escuela.backend.evaluation.exception.NotExamBoardTribunalMemberException;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.GradeItemRepository;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import com.red_escuela.backend.shared.exception.ForbiddenException;
import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExamBoardFinalGradesRecorder {

    private final ExamBoardRepository examBoardRepository;
    private final ExamBoardInstructorRepository examBoardInstructorRepository;
    private final ExamBoardRegistrationRepository examBoardRegistrationRepository;
    private final GradeItemRepository gradeItemRepository;
    private final GradeItemRecorder gradeItemRecorder;
    private final ExamBoardRegistrationSummaryBuilder summaryBuilder;
    private final ExamBoardTakenChecker takenChecker;

    public ExamBoardFinalGradesRecorder(
            ExamBoardRepository examBoardRepository,
            ExamBoardInstructorRepository examBoardInstructorRepository,
            ExamBoardRegistrationRepository examBoardRegistrationRepository,
            GradeItemRepository gradeItemRepository,
            GradeItemRecorder gradeItemRecorder,
            ExamBoardRegistrationSummaryBuilder summaryBuilder,
            ExamBoardTakenChecker takenChecker) {
        this.examBoardRepository = examBoardRepository;
        this.examBoardInstructorRepository = examBoardInstructorRepository;
        this.examBoardRegistrationRepository = examBoardRegistrationRepository;
        this.gradeItemRepository = gradeItemRepository;
        this.gradeItemRecorder = gradeItemRecorder;
        this.summaryBuilder = summaryBuilder;
        this.takenChecker = takenChecker;
    }

    @Transactional
    public ExamBoardFinalGradesResponse record(
            Long examBoardId, Long instructorId, List<ExamBoardFinalGradeEntryRequest> entries) {
        ExamBoard board = examBoardRepository.findByIdForUpdate(examBoardId)
                .orElseThrow(() -> new ExamBoardNotFoundException(examBoardId));
        requireOpenBoardOfTribunalMember(board, instructorId);
        requireDistinctRegistrations(entries);

        Map<Long, ExamBoardRegistration> registrations = lockRegistrations(examBoardId, entries);
        List<GradeItem> boardGrades = gradeItemRepository.findByExamBoardId(examBoardId);
        entries.stream()
                .sorted(Comparator.comparing((ExamBoardFinalGradeEntryRequest entry) -> isCourse(registrations.get(entry.registrationId())))
                        .thenComparing(entry -> enrollmentIdOf(registrations.get(entry.registrationId()))))
                .forEach(entry -> recordRow(board, instructorId, entry, registrations, boardGrades));

        List<ExamBoardRegistration> touched = entries.stream()
                .map(entry -> registrations.get(entry.registrationId()))
                .toList();
        return new ExamBoardFinalGradesResponse(summaryBuilder.build(examBoardId, touched));
    }

    private void requireOpenBoardOfTribunalMember(ExamBoard board, Long instructorId) {
        if (board.getStatus() == ExamBoardStatus.CANCELED) {
            throw new ExamBoardCanceledException(board.getId());
        }
        if (board.getStatus() != ExamBoardStatus.SCHEDULED) {
            throw new ExamBoardClosedException(board.getId());
        }
        if (!examBoardInstructorRepository.existsByExamBoardIdAndInstructorId(board.getId(), instructorId)) {
            throw new NotExamBoardTribunalMemberException(instructorId, board.getId());
        }
        takenChecker.requireTaken(board);
    }

    private void requireDistinctRegistrations(List<ExamBoardFinalGradeEntryRequest> entries) {
        Set<Long> seen = new HashSet<>();
        for (ExamBoardFinalGradeEntryRequest entry : entries) {
            if (!seen.add(entry.registrationId())) {
                throw new InvalidExamBoardFinalGradeEntryException(
                        "La inscripción " + entry.registrationId() + " aparece más de una vez en el lote");
            }
        }
    }

    private Map<Long, ExamBoardRegistration> lockRegistrations(
            Long examBoardId, List<ExamBoardFinalGradeEntryRequest> entries) {
        List<Long> ids = entries.stream().map(ExamBoardFinalGradeEntryRequest::registrationId).toList();
        Map<Long, ExamBoardRegistration> found = examBoardRegistrationRepository
                .findByExamBoardIdAndIdInForUpdate(examBoardId, ids).stream()
                .collect(Collectors.toMap(ExamBoardRegistration::getId, Function.identity()));
        for (Long id : ids) {
            if (!found.containsKey(id)) {
                throw rejected(id, new ExamBoardRegistrationNotFoundException(id));
            }
        }
        return found;
    }

    private void recordRow(
            ExamBoard board, Long instructorId, ExamBoardFinalGradeEntryRequest entry,
            Map<Long, ExamBoardRegistration> registrations, List<GradeItem> boardGrades) {
        ExamBoardRegistration registration = registrations.get(entry.registrationId());
        try {
            if (registration.getStatus() == ExamBoardRegistrationStatus.WITHDRAWN) {
                throw new ExamBoardRegistrationWithdrawnException(registration.getId());
            }
            GradeItem existing = boardGrades.stream()
                    .filter(grade -> sameEnrollment(grade, registration))
                    .findFirst()
                    .orElse(null);
            if (entry.markedAbsent()) {
                markAbsent(board, instructorId, entry, registration, existing);
            } else {
                loadScore(board, instructorId, entry, registration, existing);
            }
        } catch (BadRequestException | ConflictException | ForbiddenException | NotFoundException failure) {
            throw rejected(entry.registrationId(), failure);
        }
    }

    private void markAbsent(
            ExamBoard board, Long instructorId, ExamBoardFinalGradeEntryRequest entry,
            ExamBoardRegistration registration, GradeItem existing) {
        if (existing != null && existing.getScore() != null) {
            if (isCourse(registration)) {
                gradeItemRecorder.clearCourseEnrollmentFinal(
                        enrollmentIdOf(registration), instructorId, board.getId(), entry.reason());
            } else {
                gradeItemRecorder.clearProgramEnrollmentFinal(
                        enrollmentIdOf(registration), instructorId, board.getId(), entry.reason());
            }
        }
        registration.setStatus(ExamBoardRegistrationStatus.ABSENT);
    }

    private void loadScore(
            ExamBoard board, Long instructorId, ExamBoardFinalGradeEntryRequest entry,
            ExamBoardRegistration registration, GradeItem existing) {
        Long enrollmentId = enrollmentIdOf(registration);
        List<GradeItem> enrollmentItems = isCourse(registration)
                ? gradeItemRecorder.listByCourseEnrollment(enrollmentId)
                : gradeItemRecorder.listByProgramEnrollment(enrollmentId);
        int sequenceNumber = existing != null && existing.getSequenceNumber() != null
                ? existing.getSequenceNumber()
                : nextFinalSequence(enrollmentItems);
        CreateGradeItemRequest request = new CreateGradeItemRequest(
                GradeItemType.FINAL, sequenceNumber, null, board.getExamDate(), entry.score().value(), null,
                board.getId(), entry.reason());
        if (isCourse(registration)) {
            gradeItemRecorder.loadCourseEnrollmentGrade(enrollmentId, instructorId, request);
        } else {
            gradeItemRecorder.loadProgramEnrollmentGrade(enrollmentId, instructorId, request);
        }
        registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
    }

    private int nextFinalSequence(List<GradeItem> enrollmentItems) {
        return enrollmentItems.stream()
                .filter(item -> item.getType() == GradeItemType.FINAL)
                .map(GradeItem::getSequenceNumber)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    private ExamBoardFinalGradeRowRejectedException rejected(Long registrationId, RuntimeException failure) {
        Map<String, Object> causeDetails = failure instanceof ReportsErrorDetails reporting && reporting.details() != null
                ? reporting.details()
                : Map.of();
        return new ExamBoardFinalGradeRowRejectedException(
                registrationId, ErrorCodeResponse.codeOf(failure.getClass()), causeDetails, failure);
    }

    private boolean isCourse(ExamBoardRegistration registration) {
        return registration.getProgramSubjectSectionEnrollment() == null;
    }

    private Long enrollmentIdOf(ExamBoardRegistration registration) {
        return isCourse(registration)
                ? registration.getCourseSubjectSectionEnrollment().getId()
                : registration.getProgramSubjectSectionEnrollment().getId();
    }

    private boolean sameEnrollment(GradeItem grade, ExamBoardRegistration registration) {
        if (isCourse(registration)) {
            return grade.getCourseSubjectSectionEnrollment() != null
                    && grade.getCourseSubjectSectionEnrollment().getId().equals(enrollmentIdOf(registration));
        }
        return grade.getProgramSubjectSectionEnrollment() != null
                && grade.getProgramSubjectSectionEnrollment().getId().equals(enrollmentIdOf(registration));
    }
}
