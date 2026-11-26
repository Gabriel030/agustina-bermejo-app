package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class GraduationCertificateReissueHttpIT extends GraduationCertificateOperationsSupport {

    @Test
    void reissueRevokesTheOriginalAndCreatesALinkedCertificateWithNewCodeAndRegistryNumber() throws Exception {
        Long originalId = issueCertificate(completedEnrollment());
        GraduationCertificate original = repository.findById(originalId).orElseThrow();

        MvcResult result = postJson(BASE + "/" + originalId + "/reissue", director, VALID_REASON)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.replacesCertificateId").value(originalId.intValue()))
                .andReturn();

        String newCode = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
        entityManager.flush();
        entityManager.clear();
        GraduationCertificate replacement = repository.findByCode(newCode).orElseThrow();
        GraduationCertificate revoked = repository.findById(originalId).orElseThrow();
        assertThat(revoked.getStatus()).isEqualTo(GraduationCertificateStatus.REVOKED);
        assertThat(revoked.getRevocationReason()).isEqualTo("Error en el apellido del titular");
        assertThat(replacement.getReplacesId()).isEqualTo(originalId);
        assertThat(replacement.getCode()).isNotEqualTo(original.getCode());
        assertThat(replacement.getRegistryNumber()).isNotNull().isNotEqualTo(original.getRegistryNumber());

        send(get(BASE + "/" + originalId), director)
                .andExpect(jsonPath("$.replacedByCertificateId").value(replacement.getId().intValue()));
    }

    @Test
    void publicVerificationShowsReplacedForTheOriginalAndValidForTheReplacement() throws Exception {
        Long originalId = issueCertificate(completedEnrollment());
        String originalCode = repository.findById(originalId).orElseThrow().getCode();
        MvcResult result = postJson(BASE + "/" + originalId + "/reissue", director, VALID_REASON).andExpect(status().isOk()).andReturn();
        String newCode = JsonPath.read(result.getResponse().getContentAsString(), "$.code");

        mockMvc.perform(get("/public/documents/{code}", originalCode).with(request -> {
                    request.setRemoteAddr("198.51.100.202");
                    return request;
                }))
                .andExpect(jsonPath("$.status").value("REPLACED"));
        mockMvc.perform(get("/public/documents/{code}", newCode).with(request -> {
                    request.setRemoteAddr("198.51.100.203");
                    return request;
                }))
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void reissuingAnAlreadyRevokedCertificateIsAConflict() throws Exception {
        Long id = issueCertificate(completedEnrollment());
        postJson(BASE + "/" + id + "/revoke", director, VALID_REASON).andExpect(status().isOk());

        postJson(BASE + "/" + id + "/reissue", director, VALID_REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_NOT_REVOCABLE"));
    }

    @Test
    void reissueOfAGraduateThatIsNoLongerEligibleIsAConflict() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        Long id = issueCertificate(enrollment);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        postJson(BASE + "/" + id + "/reissue", director, VALID_REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_NOT_ELIGIBLE"));
    }

    @Test
    void reasonThatIsTooShortAfterTrimmingIsRejected() throws Exception {
        Long id = issueCertificate(completedEnrollment());

        postJson(BASE + "/" + id + "/reissue", director, "{\"reason\":\"   corto   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVOCATION_REASON_INVALID"));
    }

    @Test
    void administrativeCannotReissue() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        Long id = issueCertificate(enrollment);

        postJson(BASE + "/" + id + "/reissue", administrative, VALID_REASON).andExpect(status().isForbidden());
        postJson(BASE + "/" + id + "/reissue", director, "{\"reason\":\"corto\"}").andExpect(status().isBadRequest());
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(GraduationCertificateStatus.VALID);
    }
}
