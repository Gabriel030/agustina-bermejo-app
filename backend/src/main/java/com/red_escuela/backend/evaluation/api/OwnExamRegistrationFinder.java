package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@Transactional(readOnly = true)
public class OwnExamRegistrationFinder {

    private final ExamBoardRegistrationRepository registrationRepository;

    public OwnExamRegistrationFinder(ExamBoardRegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    public List<OwnExamRegistrationView> activeRegistrationsOf(Long studentId, LocalDateTime notBefore) {
        List<OwnExamRegistrationView> views = new ArrayList<>();
        registrationRepository.findActiveProgramRegistrationsOfStudentFrom(studentId, notBefore)
                .forEach(registration -> views.add(programViewOf(registration, studentId)));
        registrationRepository.findActiveCourseRegistrationsOfStudentFrom(studentId, notBefore)
                .forEach(registration -> views.add(courseViewOf(registration, studentId)));
        return views.stream()
                .sorted(Comparator.comparing(OwnExamRegistrationView::examDateTime)
                        .thenComparing(OwnExamRegistrationView::registrationId))
                .toList();
    }

    public Optional<OwnExamRegistrationView> findActiveOwned(Long studentId, Long registrationId, LocalDateTime notBefore) {
        return activeRegistrationsOf(studentId, notBefore).stream()
                .filter(view -> view.registrationId().equals(registrationId))
                .findFirst();
    }

    public List<OwnExamRegistrationView> attendedRegistrationsOf(Long studentId) {
        List<OwnExamRegistrationView> views = new ArrayList<>();
        registrationRepository.findAttendedProgramRegistrationsOfStudent(studentId)
                .forEach(registration -> views.add(programViewOf(registration, studentId)));
        registrationRepository.findAttendedCourseRegistrationsOfStudent(studentId)
                .forEach(registration -> views.add(courseViewOf(registration, studentId)));
        return views.stream()
                .sorted(Comparator.comparing(OwnExamRegistrationView::examDateTime).reversed()
                        .thenComparing(OwnExamRegistrationView::registrationId))
                .toList();
    }

    public Optional<OwnExamRegistrationView> findAttendedOwned(Long studentId, Long registrationId) {
        return attendedRegistrationsOf(studentId).stream()
                .filter(view -> view.registrationId().equals(registrationId))
                .findFirst();
    }

    private OwnExamRegistrationView programViewOf(ExamBoardRegistration registration, Long studentId) {
        ExamBoard board = registration.getExamBoard();
        return new OwnExamRegistrationView(registration.getId(), board.getId(), studentId,
                registration.getProgramSubjectSectionEnrollment().getProgramEnrollment().getProgram().getName(),
                board.getSubject().getName(), board.getExamDate(), board.getClassroom());
    }

    private OwnExamRegistrationView courseViewOf(ExamBoardRegistration registration, Long studentId) {
        ExamBoard board = registration.getExamBoard();
        return new OwnExamRegistrationView(registration.getId(), board.getId(), studentId,
                board.getCourseSubject().getCourse().getName(), board.getCourseSubject().getName(),
                board.getExamDate(), board.getClassroom());
    }
}
