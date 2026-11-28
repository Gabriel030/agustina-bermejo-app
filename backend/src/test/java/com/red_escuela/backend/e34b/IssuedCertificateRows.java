package com.red_escuela.backend.e34b;

import jakarta.persistence.EntityManager;

import java.util.concurrent.atomic.AtomicInteger;

public final class IssuedCertificateRows {

    private static final AtomicInteger CODES = new AtomicInteger(700_000);

    private final EntityManager entityManager;

    public IssuedCertificateRows(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private static String nextCode() {
        return "0000000000000000" + String.format("%010d", CODES.incrementAndGet());
    }

    public Long validRegular(Long studentId, Long programEnrollmentId, Long issuedByPersonId) {
        Long requestId = ((Number) entityManager.createNativeQuery("""
                insert into regular_certificate_request (student_id, kind, program_enrollment_id, requested_by_id, requested_at,
                    channel, outcome) values (:student, 'PROGRAM', :enrollment, :person, now(), 'STAFF', 'ISSUED') returning id
                """).setParameter("student", studentId).setParameter("enrollment", programEnrollmentId)
                .setParameter("person", issuedByPersonId).getSingleResult()).longValue();
        return ((Number) entityManager.createNativeQuery("""
                insert into regular_student_certificate (code, kind, program_enrollment_id, student_id, status, issued_at, expires_at,
                    validity_days, issued_by_id, issue_channel, request_id, billing_mode_at_issue, standing_basis, snapshot,
                    template_ref, pdf_sha256, pdf_path)
                values (:code, 'PROGRAM', :enrollment, :student, 'VALID', now(), now() + interval '90 days', 90, :person, 'STAFF',
                    :request, 'OFF', 'BILLING_NOT_ENFORCED', '{}'::jsonb, 'regular-v1', 'x', 'x') returning id
                """).setParameter("code", nextCode()).setParameter("enrollment", programEnrollmentId)
                .setParameter("student", studentId).setParameter("person", issuedByPersonId).setParameter("request", requestId)
                .getSingleResult()).longValue();
    }

    public Long validApprovedSubjects(Long studentId, Long programEnrollmentId, Long issuedByPersonId) {
        return ((Number) entityManager.createNativeQuery("""
                insert into approved_subjects_certificate (code, program_enrollment_id, student_id, status, issued_at, expires_at,
                    validity_days, issued_by_id, snapshot, template_ref, render_engine_version, pdf_sha256)
                values (:code, :enrollment, :student, 'VALID', now(), now() + interval '90 days', 90, :person, '{}'::jsonb,
                    'approved-v1', 'v1', 'x') returning id
                """).setParameter("code", nextCode()).setParameter("enrollment", programEnrollmentId)
                .setParameter("student", studentId).setParameter("person", issuedByPersonId).getSingleResult()).longValue();
    }

    public String regularStatus(Long id) {
        return (String) entityManager.createNativeQuery("select status from regular_student_certificate where id = :id")
                .setParameter("id", id).getSingleResult();
    }

    public String approvedSubjectsStatus(Long id) {
        return (String) entityManager.createNativeQuery("select status from approved_subjects_certificate where id = :id")
                .setParameter("id", id).getSingleResult();
    }

    public String revocationReasonOfApprovedSubjects(Long id) {
        return (String) entityManager.createNativeQuery("select revocation_reason from approved_subjects_certificate where id = :id")
                .setParameter("id", id).getSingleResult();
    }

    public Long openGraduationRequest(Long programEnrollmentId) {
        return ((Number) entityManager.createNativeQuery("""
                insert into graduation_request (program_enrollment_id, status, requested_at, version, certificate_review_status)
                values (:enrollment, 'REQUESTED', now(), 0, 'NOT_REQUIRED') returning id
                """).setParameter("enrollment", programEnrollmentId).getSingleResult()).longValue();
    }

    public String graduationRequestStatus(Long id) {
        return (String) entityManager.createNativeQuery("select status from graduation_request where id = :id")
                .setParameter("id", id).getSingleResult();
    }

    public void markApprovedSubjectsAs(Long id, String status) {
        entityManager.createNativeQuery("update approved_subjects_certificate set status = :status where id = :id")
                .setParameter("status", status).setParameter("id", id).executeUpdate();
    }

    public void markRegularAs(Long id, String status) {
        entityManager.createNativeQuery("update regular_student_certificate set status = :status where id = :id")
                .setParameter("status", status).setParameter("id", id).executeUpdate();
    }

    public long auditEntries(String action, Long entityId) {
        return ((Number) entityManager.createNativeQuery("select count(*) from audit_log where action = :action and entity_id = :id")
                .setParameter("action", action).setParameter("id", entityId).getSingleResult()).longValue();
    }

    public Long auditActor(String action, Long entityId) {
        Object actor = entityManager.createNativeQuery("select actor_person_id from audit_log where action = :action and entity_id = :id")
                .setParameter("action", action).setParameter("id", entityId).getSingleResult();
        return actor == null ? null : ((Number) actor).longValue();
    }

    public String revocationReasonOfRegular(Long id) {
        return (String) entityManager.createNativeQuery("select revocation_reason from regular_student_certificate where id = :id")
                .setParameter("id", id).getSingleResult();
    }
}
