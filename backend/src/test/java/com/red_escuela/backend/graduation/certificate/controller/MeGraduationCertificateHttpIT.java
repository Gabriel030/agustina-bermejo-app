package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class MeGraduationCertificateHttpIT extends GraduationCertificateOperationsSupport {

    private static final String ME = "/me/certificates";

    @Autowired
    CertificateFileStore fileStore;
    @Autowired
    AuditLogRepository auditLogRepository;

    private String studentToken(ProgramEnrollment enrollment) {
        Person person = enrollment.getStudent().getPerson();
        person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private long downloads(Long id, AuditAction action) {
        return auditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == action && entry.getEntityType() == AuditEntityType.GRADUATION_CERTIFICATE
                        && id.equals(entry.getEntityId()))
                .count();
    }

    @Test
    void studentListsOnlyTheirOwnCertificatesWithoutTheRevocationReason() throws Exception {
        ProgramEnrollment mine = completedEnrollment();
        ProgramEnrollment other = completedEnrollment();
        Long mineId = issueCertificate(mine);
        issueCertificate(other);

        send(get(ME), studentToken(mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mineId))
                .andExpect(jsonPath("$[0].status").value("VALID"))
                .andExpect(jsonPath("$[0].downloadable").value(true))
                .andExpect(jsonPath("$[0].previewable").value(true));
    }

    @Test
    void revokedCertificateIsListedAsNotDownloadableAndNeverExposesTheReason() throws Exception {
        ProgramEnrollment mine = completedEnrollment();
        Long id = issueCertificate(mine);
        postJson(BASE + "/" + id + "/revoke", director, VALID_REASON).andExpect(status().isOk());

        MvcResult result = send(get(ME), studentToken(mine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REVOKED"))
                .andExpect(jsonPath("$[0].downloadable").value(false))
                .andExpect(jsonPath("$[0].previewable").value(false))
                .andExpect(jsonPath("$[0].revokedAt").isNotEmpty())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Error en el apellido").doesNotContain("revocationReason");
    }

    @Test
    void studentDownloadsTheirPdfAndEveryDownloadIsAudited() throws Exception {
        ProgramEnrollment mine = completedEnrollment();
        Long id = issueCertificate(mine);
        String token = studentToken(mine);

        send(get(ME + "/" + id + "/pdf"), token).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
        send(get(ME + "/" + id + "/pdf?disposition=INLINE"), token).andExpect(status().isOk());

        assertThat(downloads(id, AuditAction.GRADUATION_CERTIFICATE_DOWNLOADED)).isEqualTo(2);
    }

    @Test
    void anotherStudentCannotDownloadAndNothingIsAudited() throws Exception {
        Long id = issueCertificate(completedEnrollment());

        send(get(ME + "/" + id + "/pdf"), studentToken(completedEnrollment())).andExpect(status().isNotFound());

        assertThat(downloads(id, AuditAction.GRADUATION_CERTIFICATE_DOWNLOADED)).isZero();
    }

    @Test
    void revokedCertificateCannotBeDownloaded() throws Exception {
        ProgramEnrollment mine = completedEnrollment();
        Long id = issueCertificate(mine);
        postJson(BASE + "/" + id + "/revoke", director, VALID_REASON).andExpect(status().isOk());

        send(get(ME + "/" + id + "/pdf"), studentToken(mine))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_REVOKED"));

        assertThat(downloads(id, AuditAction.GRADUATION_CERTIFICATE_DOWNLOADED)).isZero();
    }

    @Test
    void missingFileIsRegeneratedFromTheSnapshotWhenItMatchesTheStoredHash() throws Exception {
        ProgramEnrollment mine = completedEnrollment();
        Long id = issueCertificate(mine);
        GraduationCertificate certificate = repository.findById(id).orElseThrow();
        byte[] original = fileStore.load(certificate.getPdfPath()).orElseThrow();
        fileStore.save(certificate.getCode(), new byte[] {1, 2, 3});

        MvcResult result = send(get(ME + "/" + id + "/pdf"), studentToken(mine)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(original);
    }

    @Test
    void regeneratedPdfThatDoesNotMatchTheStoredHashIsNeverServedAndIsAudited() throws Exception {
        ProgramEnrollment mine = completedEnrollment();
        Long id = issueCertificate(mine);
        GraduationCertificate certificate = repository.findById(id).orElseThrow();
        fileStore.save(certificate.getCode(), new byte[] {1, 2, 3});
        entityManager.createNativeQuery("update graduation_certificate set pdf_sha256 = :hash where id = :id")
                .setParameter("hash", "f".repeat(64)).setParameter("id", id).executeUpdate();
        entityManager.clear();

        MvcResult result = send(get(ME + "/" + id + "/pdf"), studentToken(mine)).andExpect(status().isInternalServerError()).andReturn();

        assertThat(result.getResponse().getContentType()).doesNotContain("pdf");
        assertThat(downloads(id, AuditAction.GRADUATION_CERTIFICATE_PDF_MISMATCH)).isEqualTo(1);
        assertThat(downloads(id, AuditAction.GRADUATION_CERTIFICATE_DOWNLOADED)).isZero();
    }
}
