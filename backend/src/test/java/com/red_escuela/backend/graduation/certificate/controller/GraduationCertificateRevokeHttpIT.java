package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GraduationCertificateRevokeHttpIT extends GraduationCertificateOperationsSupport {

    @Test
    void directorRevokesAValidCertificateAndTheMarkIsStored() throws Exception {
        Long id = issueCertificate(completedEnrollment());

        postJson(BASE + "/" + id + "/revoke", director, VALID_REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"))
                .andExpect(jsonPath("$.revokedAt").isNotEmpty())
                .andExpect(jsonPath("$.revocationReason").value("Error en el apellido del titular"));

        entityManager.flush();
        entityManager.clear();
        GraduationCertificate stored = repository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(GraduationCertificateStatus.REVOKED);
        assertThat(stored.getRevokedById()).isNotNull();
        assertThat(stored.getRevokedAt()).isNotNull();
    }

    @Test
    void revokingAnAlreadyRevokedCertificateIsAConflict() throws Exception {
        Long id = issueCertificate(completedEnrollment());
        postJson(BASE + "/" + id + "/revoke", director, VALID_REASON).andExpect(status().isOk());

        postJson(BASE + "/" + id + "/revoke", director, VALID_REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_NOT_REVOCABLE"));
    }

    @Test
    void tooShortReasonIsRejected() throws Exception {
        Long id = issueCertificate(completedEnrollment());

        postJson(BASE + "/" + id + "/revoke", director, "{\"reason\":\"corto\"}").andExpect(status().isBadRequest());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(GraduationCertificateStatus.VALID);
    }

    @Test
    void administrativeCannotRevokeAndTheCertificateStaysValid() throws Exception {
        Long id = issueCertificate(completedEnrollment());

        postJson(BASE + "/" + id + "/revoke", administrative, VALID_REASON).andExpect(status().isForbidden());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(GraduationCertificateStatus.VALID);
    }

    @Test
    void unknownCertificateIsNotFound() throws Exception {
        postJson(BASE + "/999999999/revoke", director, VALID_REASON)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_NOT_FOUND"));
    }

    @Test
    void publicVerificationShowsRevokedForTheRevokedCertificate() throws Exception {
        Long id = issueCertificate(completedEnrollment());
        String code = repository.findById(id).orElseThrow().getCode();
        postJson(BASE + "/" + id + "/revoke", director, VALID_REASON).andExpect(status().isOk());

        mockMvc.perform(get("/public/certificates/{code}", code).with(request -> {
                    request.setRemoteAddr("198.51.100.201");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }
}
