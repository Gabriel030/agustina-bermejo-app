package com.red_escuela.backend.enrollment.entity;
import com.red_escuela.backend.identity.entity.Person;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.entity.ExceptionType;
import com.red_escuela.backend.enrollment.exception.DirectorApprovalSubjectRequiredException;
import com.red_escuela.backend.enrollment.exception.InvalidDirectorApprovalRequestStateException;

@Getter
@Setter
@Entity
@Table(name = "director_approval_request")
public class DirectorApprovalRequest {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ExceptionType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_enrollment_id", nullable = false)
    private ProgramEnrollment programEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(name = "justification", length = 1000, nullable = false)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DirectorApprovalRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Person requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private Person decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_notes", length = 500)
    private String decisionNotes;

    public static DirectorApprovalRequest open(ExceptionType type, ProgramEnrollment programEnrollment, Subject subject,
                                               String justification, Person requestedBy, LocalDateTime requestedAt) {
        programEnrollment.requireActive();
        if (subject == null) {
            throw new DirectorApprovalSubjectRequiredException(type);
        }
        DirectorApprovalRequest request = new DirectorApprovalRequest();
        request.type = type;
        request.programEnrollment = programEnrollment;
        request.subject = subject;
        request.justification = justification;
        request.status = DirectorApprovalRequestStatus.REQUESTED;
        request.requestedBy = requestedBy;
        request.requestedAt = requestedAt;
        return request;
    }

    public void decide(DirectorApprovalRequestStatus decision, Person director, String notes, LocalDateTime decidedAt) {
        boolean validDecision = decision == DirectorApprovalRequestStatus.APPROVED || decision == DirectorApprovalRequestStatus.REJECTED;
        if (status != DirectorApprovalRequestStatus.REQUESTED || !validDecision) {
            throw new InvalidDirectorApprovalRequestStateException(id);
        }
        status = decision;
        decidedBy = director;
        decisionNotes = notes;
        this.decidedAt = decidedAt;
    }

    public Person student() {
        return programEnrollment.getStudent().getPerson();
    }

    public String describe() {
        Person student = student();
        String description = type.displayName() + " de " + student.getName() + " " + student.getLastname();
        return subject == null ? description : description + " en " + subject.getName();
    }

    public boolean hasDecisionNotes() {
        return decisionNotes != null && !decisionNotes.isBlank();
    }
}
