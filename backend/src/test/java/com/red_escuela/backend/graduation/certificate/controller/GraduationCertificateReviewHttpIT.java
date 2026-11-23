package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.graduation.api.HistoricalGraduation;
import com.red_escuela.backend.graduation.api.HistoricalGraduationRecorder;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.domain.DocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GraduationCertificateReviewHttpIT extends GraduationCertificateOperationsSupport {

    private static final String REVIEWS = "/graduation-certificate-reviews";
    private static final String DISABLE_BODY = "{\"reason\":\"Documentación incompleta del alumno\"}";

    @Autowired
    GraduationRequestRepository graduationRequestRepository;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    HistoricalGraduationRecorder historicalGraduationRecorder;

    private GraduationRequest approvedRequest(ProgramEnrollment enrollment, CertificateReviewStatus reviewStatus) {
        GraduationRequest request = new GraduationRequest();
        request.setProgramEnrollment(enrollment);
        request.setStatus(GraduationRequestStatus.APPROVED);
        request.setRequestedAt(LocalDateTime.now());
        request.setCertificateReviewStatus(reviewStatus);
        GraduationRequest saved = graduationRequestRepository.saveAndFlush(request);
        entityManager.clear();
        return saved;
    }

    private String issueBody(ProgramEnrollment enrollment) {
        return "{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollment.getId() + "}";
    }

    private CertificateReviewStatus reviewStatusOf(Long graduationRequestId) {
        entityManager.flush();
        entityManager.clear();
        return graduationRequestRepository.findById(graduationRequestId).orElseThrow().getCertificateReviewStatus();
    }

    private List<AuditLogEntry> auditOf(Long graduationRequestId) {
        return auditLogRepository.findByEntityTypeAndEntityIdInOrderByOccurredAtDescIdDesc(
                AuditEntityType.GRADUATION_REQUEST, List.of(graduationRequestId), Pageable.unpaged()).getContent();
    }

    @Test
    void pendingReviewBlocksIssuePreviewAndBatchAndAppearsInEligibleList() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        approvedRequest(enrollment, CertificateReviewStatus.PENDING_REVIEW);

        postJson(BASE, director, issueBody(enrollment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_REVIEW_REQUIRED"));
        postJson(BASE + "/preview", director, issueBody(enrollment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_REVIEW_REQUIRED"));
        postJson(BASE + "/batch", director, "{\"items\":[" + issueBody(enrollment) + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].errorCode").value("GRADUATION_CERTIFICATE_REVIEW_REQUIRED"));
        send(get(BASE + "/eligible").param("status", "PENDING_REVIEW").param("query", enrollment.getStudent().getPerson().getDni()),
                director)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].enrollmentId").value(enrollment.getId()))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_REVIEW"));
    }

    @Test
    void disabledBlocksIssueEvenWithoutDebtAndAppearsAsDisabledInEligibleList() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        approvedRequest(enrollment, CertificateReviewStatus.DISABLED);

        postJson(BASE, director, issueBody(enrollment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_REVIEW_REQUIRED"));
        send(get(BASE + "/eligible").param("status", "DISABLED").param("query", enrollment.getStudent().getPerson().getDni()), director)
                .andExpect(jsonPath("$.content[0].status").value("DISABLED"));
        send(get(BASE + "/eligible").param("status", "PENDING_REVIEW").param("query", enrollment.getStudent().getPerson().getDni()),
                director)
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void enabledAndNotRequiredIssueNormallyAndDebtStillBlocks() throws Exception {
        ProgramEnrollment enabled = completedEnrollment();
        ProgramEnrollment notRequired = completedEnrollment();
        approvedRequest(enabled, CertificateReviewStatus.ENABLED);
        approvedRequest(notRequired, CertificateReviewStatus.NOT_REQUIRED);

        postJson(BASE, director, issueBody(enabled)).andExpect(status().isCreated());
        postJson(BASE, director, issueBody(notRequired)).andExpect(status().isCreated());

        ProgramEnrollment enabledWithDebt = completedEnrollment();
        approvedRequest(enabledWithDebt, CertificateReviewStatus.ENABLED);
        org.mockito.Mockito.when(debtCheck.hasDebt(enabledWithDebt.getStudent().getId())).thenReturn(true);
        postJson(BASE, director, issueBody(enabledWithDebt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_BLOCKED_BY_DEBT"));
    }

    @Test
    void enablingUnblocksIssuanceAndStoresReviewerAndAudit() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        GraduationRequest request = approvedRequest(enrollment, CertificateReviewStatus.PENDING_REVIEW);

        postJson(REVIEWS + "/" + request.getId() + "/enable", director, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("ENABLED"))
                .andExpect(jsonPath("$.reviewedById").isNotEmpty())
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        postJson(BASE, director, issueBody(enrollment)).andExpect(status().isCreated());

        GraduationRequest stored = graduationRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(stored.getCertificateReviewedBy()).isNotNull();
        assertThat(stored.getCertificateReviewedAt()).isNotNull();
        List<AuditLogEntry> audit = auditOf(request.getId());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo(AuditAction.GRADUATION_CERTIFICATE_REVIEW_ENABLED);
        assertThat(audit.get(0).getActorPersonId()).isEqualTo(stored.getCertificateReviewedBy().getId());
        assertThat(audit.get(0).getBeforeValue()).isEqualTo("PENDING_REVIEW");
        assertThat(audit.get(0).getAfterValue()).isEqualTo("ENABLED");
    }

    @Test
    void disablingStoresReviewerReasonAndAuditAndCanBeRevertedWhileNoCertificateWasIssued() throws Exception {
        GraduationRequest request = approvedRequest(completedEnrollment(), CertificateReviewStatus.PENDING_REVIEW);

        postJson(REVIEWS + "/" + request.getId() + "/disable", director, DISABLE_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("DISABLED"))
                .andExpect(jsonPath("$.reviewedById").isNotEmpty());
        assertThat(reviewStatusOf(request.getId())).isEqualTo(CertificateReviewStatus.DISABLED);
        GraduationRequest disabled = graduationRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(disabled.getCertificateReviewedBy()).isNotNull();
        assertThat(disabled.getCertificateReviewedAt()).isNotNull();
        List<AuditLogEntry> audit = auditOf(request.getId());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo(AuditAction.GRADUATION_CERTIFICATE_REVIEW_DISABLED);
        assertThat(audit.get(0).getReason()).isEqualTo("Documentación incompleta del alumno");

        postJson(REVIEWS + "/" + request.getId() + "/enable", director, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewStatus").value("ENABLED"));
        assertThat(auditOf(request.getId())).hasSize(2);
    }

    @Test
    void disableRequiresAReasonOfAtLeastTenCharactersAfterTrimming() throws Exception {
        GraduationRequest request = approvedRequest(completedEnrollment(), CertificateReviewStatus.PENDING_REVIEW);

        for (String body : new String[] {"{}", "{\"reason\":\"corto\"}", "{\"reason\":\"          \"}", "{\"reason\":\"  corto   \"}"}) {
            postJson(REVIEWS + "/" + request.getId() + "/disable", director, body).andExpect(status().isBadRequest());
        }
        assertThat(reviewStatusOf(request.getId())).isEqualTo(CertificateReviewStatus.PENDING_REVIEW);
        assertThat(auditOf(request.getId())).isEmpty();
    }

    @Test
    void cannotDecideWhenAValidCertificateWasIssuedOrWhenNotRequired() throws Exception {
        ProgramEnrollment issued = completedEnrollment();
        GraduationRequest issuedRequest = approvedRequest(issued, CertificateReviewStatus.ENABLED);
        issueCertificate(issued);
        GraduationRequest notRequired = approvedRequest(completedEnrollment(), CertificateReviewStatus.NOT_REQUIRED);

        postJson(REVIEWS + "/" + issuedRequest.getId() + "/disable", director, DISABLE_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_GRADUATION_CERTIFICATE_REVIEW_DECISION"));
        postJson(REVIEWS + "/" + issuedRequest.getId() + "/enable", director, "").andExpect(status().isConflict());
        postJson(REVIEWS + "/" + notRequired.getId() + "/enable", director, "")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_GRADUATION_CERTIFICATE_REVIEW_DECISION"));
        assertThat(reviewStatusOf(issuedRequest.getId())).isEqualTo(CertificateReviewStatus.ENABLED);
        assertThat(auditOf(issuedRequest.getId())).isEmpty();
    }

    @Test
    void nonDirectorRolesCannotListOrDecide() throws Exception {
        GraduationRequest request = approvedRequest(completedEnrollment(), CertificateReviewStatus.PENDING_REVIEW);

        send(get(REVIEWS), administrative).andExpect(status().isForbidden());
        postJson(REVIEWS + "/" + request.getId() + "/enable", administrative, "").andExpect(status().isForbidden());
        postJson(REVIEWS + "/" + request.getId() + "/disable", administrative, DISABLE_BODY).andExpect(status().isForbidden());
        postJson(REVIEWS + "/batch", administrative,
                "{\"graduationRequestIds\":[" + request.getId() + "],\"decision\":\"ENABLE\"}").andExpect(status().isForbidden());
        assertThat(reviewStatusOf(request.getId())).isEqualTo(CertificateReviewStatus.PENDING_REVIEW);
    }

    @Test
    void batchReturnsPartialResultPerItem() throws Exception {
        GraduationRequest pending = approvedRequest(completedEnrollment(), CertificateReviewStatus.PENDING_REVIEW);
        GraduationRequest notRequired = approvedRequest(completedEnrollment(), CertificateReviewStatus.NOT_REQUIRED);

        postJson(REVIEWS + "/batch", director, "{\"graduationRequestIds\":[" + pending.getId() + "," + notRequired.getId()
                + ",999999999],\"decision\":\"ENABLE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(3))
                .andExpect(jsonPath("$.decided").value(1))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.results[0].outcome").value("DECIDED"))
                .andExpect(jsonPath("$.results[0].review.reviewStatus").value("ENABLED"))
                .andExpect(jsonPath("$.results[1].outcome").value("FAILED"))
                .andExpect(jsonPath("$.results[1].errorCode").value("INVALID_GRADUATION_CERTIFICATE_REVIEW_DECISION"))
                .andExpect(jsonPath("$.results[2].errorCode").value("GRADUATION_REQUEST_NOT_FOUND"));
        assertThat(reviewStatusOf(pending.getId())).isEqualTo(CertificateReviewStatus.ENABLED);
        assertThat(reviewStatusOf(notRequired.getId())).isEqualTo(CertificateReviewStatus.NOT_REQUIRED);
    }

    @Test
    void batchDisableRequiresReasonAndAppliesIt() throws Exception {
        GraduationRequest request = approvedRequest(completedEnrollment(), CertificateReviewStatus.PENDING_REVIEW);
        String ids = "\"graduationRequestIds\":[" + request.getId() + "]";

        postJson(REVIEWS + "/batch", director, "{" + ids + ",\"decision\":\"DISABLE\"}").andExpect(status().isBadRequest());
        postJson(REVIEWS + "/batch", director, "{" + ids + ",\"decision\":\"DISABLE\",\"reason\":\"          \"}")
                .andExpect(status().isBadRequest());
        assertThat(reviewStatusOf(request.getId())).isEqualTo(CertificateReviewStatus.PENDING_REVIEW);

        postJson(REVIEWS + "/batch", director, "{" + ids + ",\"decision\":\"DISABLE\",\"reason\":\"Documentación incompleta del alumno\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decided").value(1));
        assertThat(reviewStatusOf(request.getId())).isEqualTo(CertificateReviewStatus.DISABLED);
        assertThat(auditOf(request.getId()).get(0).getReason()).isEqualTo("Documentación incompleta del alumno");
    }

    @Test
    void batchAcceptsFiftyAndRejectsFiftyOneOrEmptyLists() throws Exception {
        String fifty = LongStream.rangeClosed(900_000_001L, 900_000_050L).mapToObj(String::valueOf).collect(Collectors.joining(","));
        String fiftyOne = fifty + ",900000051";

        postJson(REVIEWS + "/batch", director, "{\"graduationRequestIds\":[" + fifty + "],\"decision\":\"ENABLE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(50))
                .andExpect(jsonPath("$.failed").value(50));
        postJson(REVIEWS + "/batch", director, "{\"graduationRequestIds\":[" + fiftyOne + "],\"decision\":\"ENABLE\"}")
                .andExpect(status().isBadRequest());
        postJson(REVIEWS + "/batch", director, "{\"graduationRequestIds\":[],\"decision\":\"ENABLE\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByStatusAndQueryAndExcludesNotRequired() throws Exception {
        ProgramEnrollment pendingEnrollment = completedEnrollment();
        GraduationRequest pending = approvedRequest(pendingEnrollment, CertificateReviewStatus.PENDING_REVIEW);
        GraduationRequest disabled = approvedRequest(completedEnrollment(), CertificateReviewStatus.DISABLED);
        approvedRequest(completedEnrollment(), CertificateReviewStatus.NOT_REQUIRED);

        send(get(REVIEWS).param("status", "PENDING_REVIEW"), director)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.graduationRequestId == " + pending.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.graduationRequestId == " + disabled.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.reviewStatus == 'NOT_REQUIRED')]").isEmpty());
        send(get(REVIEWS).param("query", pendingEnrollment.getStudent().getPerson().getDni()), director)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].graduationRequestId").value(pending.getId()))
                .andExpect(jsonPath("$.content[0].documentNumber").value(pendingEnrollment.getStudent().getPerson().getDni()));
        send(get(REVIEWS).param("status", "NOT_REQUIRED"), director)
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void importedGraduationIsRecordedPendingReviewAndBlocksCertificate() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        LocalDateTime completion = enrollment.getCompletionDate();

        Long id = historicalGraduationRecorder.recordGraduation(new HistoricalGraduation(DocumentType.DNI,
                enrollment.getStudent().getPerson().getDni(), enrollment.getProgram().getName(), completion.toLocalDate(),
                "REG-1", null));

        assertThat(reviewStatusOf(id)).isEqualTo(CertificateReviewStatus.PENDING_REVIEW);
        postJson(BASE, director, issueBody(enrollment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_REVIEW_REQUIRED"));
    }
}
