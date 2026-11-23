package com.red_escuela.backend.evaluation.api;

import com.red_escuela.backend.academicstructure.service.CurrentInstructorService;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.specialist.ExamBoardManager;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class ExamBoardAttendanceReader {

    private static final Set<String> STAFF_AUTHORITIES = Set.of("ROLE_DIRECTOR", "ROLE_ADMINISTRATIVO");

    private final ExamBoardManager examBoardManager;
    private final ExamBoardRegistrationRepository registrationRepository;
    private final CurrentInstructorService currentInstructorService;

    public ExamBoardAttendanceReader(ExamBoardManager examBoardManager, ExamBoardRegistrationRepository registrationRepository,
                                     CurrentInstructorService currentInstructorService) {
        this.examBoardManager = examBoardManager;
        this.registrationRepository = registrationRepository;
        this.currentInstructorService = currentInstructorService;
    }

    public void requireAccess(Long examBoardId) {
        examBoardManager.get(examBoardId);
        if (isStaff()) {
            return;
        }
        examBoardManager.requireTribunalMember(examBoardId, currentInstructorService.resolveCurrentInstructor().getId());
    }

    public ExamBoardAttendanceView attendanceOf(Long examBoardId) {
        requireAccess(examBoardId);
        ExamBoard board = examBoardManager.get(examBoardId);
        List<ExamBoardAttendeeView> attendees = registrationRepository.findByExamBoardId(examBoardId).stream()
                .map(this::toAttendee)
                .toList();
        String subjectName = board.getSubject() != null ? board.getSubject().getName() : board.getCourseSubject().getName();
        return new ExamBoardAttendanceView(board.getId(), board.getStatus() == ExamBoardStatus.CLOSED,
                board.getExamDate().toLocalDate(), subjectName, attendees);
    }

    private ExamBoardAttendeeView toAttendee(ExamBoardRegistration registration) {
        Long studentId = registration.getProgramSubjectSectionEnrollment() != null
                ? registration.getProgramSubjectSectionEnrollment().getStudent().getId()
                : registration.getCourseSubjectSectionEnrollment().getStudent().getId();
        return new ExamBoardAttendeeView(registration.getId(), studentId, attendanceOf(registration.getStatus()));
    }

    private static ExamBoardAttendance attendanceOf(ExamBoardRegistrationStatus status) {
        return switch (status) {
            case GRADED -> ExamBoardAttendance.PRESENT;
            case ABSENT -> ExamBoardAttendance.ABSENT;
            case REGISTERED, WITHDRAWN -> ExamBoardAttendance.NOT_ATTENDING;
        };
    }

    public boolean actorIsStaff() {
        return isStaff();
    }

    private static boolean isStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(STAFF_AUTHORITIES::contains);
    }
}
