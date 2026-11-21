package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class StudentExamBoardReader {

    private static final List<EnrollmentStatus> ATTENDING_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);

    private final ExamBoardRegistrationRepository registrationRepository;
    private final ExamBoardRepository examBoardRepository;
    private final Clock clock;

    public StudentExamBoardReader(ExamBoardRegistrationRepository registrationRepository,
                                  ExamBoardRepository examBoardRepository, Clock clock) {
        this.registrationRepository = registrationRepository;
        this.examBoardRepository = examBoardRepository;
        this.clock = clock;
    }

    public List<StudentExamBoardView> listForStudent(Long studentId, LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now(clock);

        Map<Long, StudentExamBoardView> viewsByBoard = new LinkedHashMap<>();
        List<ExamBoardRegistration> registrations = new ArrayList<>(
                registrationRepository.findProgramRegistrationsOfStudentBetween(studentId, start, endExclusive));
        registrations.addAll(registrationRepository.findCourseRegistrationsOfStudentBetween(studentId, start, endExclusive));
        registrations.stream()
                .sorted(Comparator.comparing((ExamBoardRegistration registration) -> isRegistered(registration)).reversed())
                .forEach(registration -> viewsByBoard.putIfAbsent(registration.getExamBoard().getId(),
                        toRegisteredView(registration, now)));

        List<ExamBoard> openBoards = new ArrayList<>(examBoardRepository.findOpenProgramBoardsForStudent(
                studentId, ATTENDING_STATUSES, start, endExclusive, now));
        openBoards.addAll(examBoardRepository.findOpenCourseBoardsForStudent(
                studentId, ATTENDING_STATUSES, start, endExclusive, now));
        openBoards.forEach(board -> viewsByBoard.putIfAbsent(board.getId(), toOpenView(board)));

        return viewsByBoard.values().stream()
                .sorted(Comparator.comparing(StudentExamBoardView::examDateTime)
                        .thenComparing(StudentExamBoardView::boardId))
                .toList();
    }

    private boolean isRegistered(ExamBoardRegistration registration) {
        return registration.getStatus() == ExamBoardRegistrationStatus.REGISTERED;
    }

    private StudentExamBoardView toRegisteredView(ExamBoardRegistration registration, LocalDateTime now) {
        ExamBoard board = registration.getExamBoard();
        return view(board, registration.getStatus().name(), isRegistrationOpen(board, now));
    }

    private StudentExamBoardView toOpenView(ExamBoard board) {
        return view(board, null, true);
    }

    private StudentExamBoardView view(ExamBoard board, String registrationStatus, boolean registrationOpen) {
        String subjectName = board.getSubject() != null ? board.getSubject().getName() : board.getCourseSubject().getName();
        return new StudentExamBoardView(board.getId(), subjectName, board.getExamDate(), board.getClassroom(),
                board.getStatus().name(), registrationStatus, registrationOpen);
    }

    private boolean isRegistrationOpen(ExamBoard board, LocalDateTime now) {
        return board.getStatus() == ExamBoardStatus.SCHEDULED
                && (board.getRegistrationStart() == null || !now.isBefore(board.getRegistrationStart()))
                && (board.getRegistrationEnd() == null || !now.isAfter(board.getRegistrationEnd()));
    }
}
