package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendance;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceView;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendeeView;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateCandidateAvailability;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateCandidateResponse;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateBoardNotHeldException;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.verification.specialist.DocumentMasker;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamCertificateCandidateLister {

    private final ExamBoardAttendanceReader boardReader;
    private final ExamCertificateRepository repository;
    private final ExamCertificatePaymentsGate paymentsGate;
    private final StudentFinder studentFinder;
    private final Clock clock;

    public ExamCertificateCandidateLister(ExamBoardAttendanceReader boardReader, ExamCertificateRepository repository,
                                          ExamCertificatePaymentsGate paymentsGate, StudentFinder studentFinder, Clock clock) {
        this.boardReader = boardReader;
        this.repository = repository;
        this.paymentsGate = paymentsGate;
        this.studentFinder = studentFinder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ExamCertificateCandidateResponse> candidates(Long examBoardId) {
        ExamBoardAttendanceView board = boardReader.attendanceOf(examBoardId);
        if (!board.held()) {
            throw new ExamCertificateBoardNotHeldException();
        }
        List<ExamBoardAttendeeView> listed = board.attendees().stream()
                .filter(attendee -> attendee.attendance() != ExamBoardAttendance.NOT_ATTENDING)
                .toList();
        Set<Long> withDebt = paymentsGate.studentsWithDebt(listed.stream()
                .filter(attendee -> attendee.attendance() == ExamBoardAttendance.PRESENT)
                .map(ExamBoardAttendeeView::studentId).toList());
        Map<Long, ExamCertificate> latest = latestByRegistration(examBoardId, listed);
        LocalDateTime now = LocalDateTime.now(clock);
        return listed.stream()
                .map(attendee -> toCandidate(attendee, latest.get(attendee.registrationId()), withDebt, now))
                .sorted(Comparator.comparing(ExamCertificateCandidateResponse::studentName))
                .toList();
    }

    private Map<Long, ExamCertificate> latestByRegistration(Long examBoardId, List<ExamBoardAttendeeView> listed) {
        if (listed.isEmpty()) {
            return Map.of();
        }
        return repository.findByExamBoardIdAndRegistrationIdIn(examBoardId, listed.stream().map(ExamBoardAttendeeView::registrationId).toList())
                .stream()
                .collect(Collectors.toMap(ExamCertificate::getRegistrationId, Function.identity(),
                        (left, right) -> left.getId() > right.getId() ? left : right));
    }

    private ExamCertificateCandidateResponse toCandidate(ExamBoardAttendeeView attendee, ExamCertificate certificate, Set<Long> withDebt,
                                                         LocalDateTime now) {
        Student student = studentFinder.requireById(attendee.studentId());
        Person person = student.getPerson();
        ExamCertificateCandidateAvailability availability = availabilityOf(attendee, certificate, withDebt, now);
        boolean shown = certificate != null && (availability == ExamCertificateCandidateAvailability.ALREADY_ISSUED
                || availability == ExamCertificateCandidateAvailability.REVOKED);
        return new ExamCertificateCandidateResponse(attendee.registrationId(), student.getId(),
                (person.getLastname() + ", " + person.getName()).toUpperCase(Locale.ROOT), DocumentMasker.mask(person.getDni()), availability,
                shown ? certificate.getId() : null, shown ? certificate.getIssuedAt() : null,
                shown && availability == ExamCertificateCandidateAvailability.REVOKED ? certificate.getRevokedAt() : null,
                shown && availability == ExamCertificateCandidateAvailability.REVOKED ? certificate.getRevocationReason() : null);
    }

    private ExamCertificateCandidateAvailability availabilityOf(ExamBoardAttendeeView attendee, ExamCertificate certificate,
                                                                Set<Long> withDebt, LocalDateTime now) {
        if (attendee.attendance() == ExamBoardAttendance.ABSENT) {
            return ExamCertificateCandidateAvailability.ABSENT;
        }
        boolean valid = Optional.ofNullable(certificate)
                .filter(current -> current.getStatus() == DocumentValidityStatus.VALID && !current.isExpiredAt(now)).isPresent();
        if (valid) {
            return ExamCertificateCandidateAvailability.ALREADY_ISSUED;
        }
        if (withDebt.contains(attendee.studentId())) {
            return ExamCertificateCandidateAvailability.NOT_ELIGIBLE_DEBT;
        }
        boolean revoked = certificate != null && certificate.getStatus() == DocumentValidityStatus.REVOKED;
        return revoked ? ExamCertificateCandidateAvailability.REVOKED : ExamCertificateCandidateAvailability.AVAILABLE;
    }
}
