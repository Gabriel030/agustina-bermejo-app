package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendance;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceView;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendeeView;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchItemResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchOutcome;
import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.IssueExamCertificateBatchRequest;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateBoardNotHeldException;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateIssuer.ExamCertificateIssueCommand;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamCertificateBatchIssuer {

    private final ExamBoardAttendanceReader boardReader;
    private final ExamCertificateRepository repository;
    private final ExamCertificatePaymentsGate paymentsGate;
    private final ExamCertificateIssuer issuer;
    private final StudentFinder studentFinder;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public ExamCertificateBatchIssuer(ExamBoardAttendanceReader boardReader, ExamCertificateRepository repository,
                                      ExamCertificatePaymentsGate paymentsGate, ExamCertificateIssuer issuer,
                                      StudentFinder studentFinder, AuditTrail auditTrail, Clock clock) {
        this.boardReader = boardReader;
        this.repository = repository;
        this.paymentsGate = paymentsGate;
        this.issuer = issuer;
        this.studentFinder = studentFinder;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public ExamCertificateBatchResponse issueBatch(Long examBoardId, IssueExamCertificateBatchRequest request) {
        ExamBoardAttendanceView board = boardReader.attendanceOf(examBoardId);
        if (!board.held()) {
            throw new ExamCertificateBoardNotHeldException();
        }
        Set<Long> requested = new LinkedHashSet<>(request.registrationIds());
        Map<Long, ExamBoardAttendeeView> attendees = board.attendees().stream()
                .collect(Collectors.toMap(ExamBoardAttendeeView::registrationId, Function.identity()));
        List<ExamBoardAttendeeView> present = requested.stream().map(attendees::get)
                .filter(attendee -> attendee != null && attendee.attendance() == ExamBoardAttendance.PRESENT).toList();
        Set<Long> withDebt = paymentsGate.studentsWithDebt(present.stream().map(ExamBoardAttendeeView::studentId).toList());
        Map<Long, ExamCertificate> latest = latestOf(examBoardId, present);
        LocalDateTime now = LocalDateTime.now(clock);
        DocumentOrigin origin = boardReader.actorIsStaff() ? DocumentOrigin.ISSUED_BY_STAFF : DocumentOrigin.ISSUED_BY_TEACHER;
        List<ExamCertificateBatchItemResponse> items = new ArrayList<>();
        int issued = 0;
        for (Long registrationId : requested) {
            ExamBoardAttendeeView attendee = attendees.get(registrationId);
            if (attendee == null || attendee.attendance() == ExamBoardAttendance.NOT_ATTENDING) {
                items.add(item(registrationId, null, ExamCertificateBatchOutcome.NOT_REGISTERED, null));
                continue;
            }
            if (attendee.attendance() == ExamBoardAttendance.ABSENT) {
                items.add(item(registrationId, attendee, ExamCertificateBatchOutcome.ABSENT, null));
                continue;
            }
            ExamCertificate current = latest.get(registrationId);
            if (current != null && current.getStatus() == DocumentValidityStatus.VALID && !current.isExpiredAt(now)) {
                items.add(item(registrationId, attendee, ExamCertificateBatchOutcome.ALREADY_ISSUED, current.getId()));
                continue;
            }
            if (withDebt.contains(attendee.studentId())) {
                items.add(item(registrationId, attendee, ExamCertificateBatchOutcome.DEBT, null));
                continue;
            }
            if (current != null && current.getStatus() == DocumentValidityStatus.VALID) {
                current.expire();
                repository.saveAndFlush(current);
            }
            ExamCertificate created = issuer.issue(new ExamCertificateIssueCommand(registrationId, examBoardId, attendee.studentId(),
                    board.subjectName(), board.examDate(), request.recipientType(), normalized(request.recipientText()), origin));
            items.add(item(registrationId, attendee, ExamCertificateBatchOutcome.ISSUED, created.getId()));
            issued++;
        }
        auditTrail.record(new AuditRecord(AuditAction.EXAM_CERTIFICATE_BATCH_ISSUED, AuditEntityType.EXAM_CERTIFICATE, examBoardId,
                null, String.valueOf(issued), "requested=" + requested.size()));
        return new ExamCertificateBatchResponse(issued, items.size() - issued, items);
    }

    private Map<Long, ExamCertificate> latestOf(Long examBoardId, List<ExamBoardAttendeeView> present) {
        if (present.isEmpty()) {
            return Map.of();
        }
        return repository.findByExamBoardIdAndRegistrationIdIn(examBoardId, present.stream().map(ExamBoardAttendeeView::registrationId).toList())
                .stream()
                .collect(Collectors.toMap(ExamCertificate::getRegistrationId, Function.identity(),
                        (left, right) -> left.getId() > right.getId() ? left : right));
    }

    private ExamCertificateBatchItemResponse item(Long registrationId, ExamBoardAttendeeView attendee, ExamCertificateBatchOutcome outcome,
                                                  Long certificateId) {
        return new ExamCertificateBatchItemResponse(registrationId, attendee == null ? null : nameOf(attendee), outcome, certificateId);
    }

    private String nameOf(ExamBoardAttendeeView attendee) {
        Person person = studentFinder.requireById(attendee.studentId()).getPerson();
        return (person.getLastname() + ", " + person.getName()).toUpperCase(Locale.ROOT);
    }

    private static String normalized(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
