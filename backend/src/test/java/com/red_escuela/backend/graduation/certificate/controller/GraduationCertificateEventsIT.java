package com.red_escuela.backend.graduation.certificate.controller;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.graduation.events.GraduationCertificateIssuedEvent;
import com.red_escuela.backend.graduation.events.GraduationCertificateReissuedEvent;
import com.red_escuela.backend.graduation.events.GraduationCertificateRevokedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@RecordApplicationEvents
class GraduationCertificateEventsIT extends GraduationCertificateOperationsSupport {

    @Autowired
    ApplicationEvents events;

    @Test
    void issuingPublishesOneIssuedEventForTheCertificateHolder() throws Exception {
        var enrollment = completedEnrollment();

        Long id = issueCertificate(enrollment);

        assertThat(events.stream(GraduationCertificateIssuedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.certificateId()).isEqualTo(id);
            assertThat(event.studentPersonId()).isEqualTo(enrollment.getStudent().getPerson().getId());
        });
    }

    @Test
    void revokingPublishesARevokedEventThatCarriesNoReason() throws Exception {
        Long id = issueCertificate(completedEnrollment());

        postJson(BASE + "/" + id + "/revoke", director, VALID_REASON).andExpect(status().isOk());

        assertThat(events.stream(GraduationCertificateRevokedEvent.class)).singleElement()
                .satisfies(event -> assertThat(event.certificateId()).isEqualTo(id));
        assertThat(events.stream(GraduationCertificateRevokedEvent.class).map(Object::toString).toList())
                .noneMatch(text -> text.contains("apellido"));
    }

    @Test
    void reissuingPublishesOnlyAReissuedEventAndNotAnIssuedOneForTheReplacement() throws Exception {
        Long id = issueCertificate(completedEnrollment());
        events.clear();

        MvcResult result = postJson(BASE + "/" + id + "/reissue", director, VALID_REASON).andExpect(status().isOk()).andReturn();
        Number replacementId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        assertThat(events.stream(GraduationCertificateReissuedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.certificateId()).isEqualTo(replacementId.longValue());
            assertThat(event.replacedCertificateId()).isEqualTo(id);
        });
        assertThat(events.stream(GraduationCertificateIssuedEvent.class)).isEmpty();
        assertThat(events.stream(GraduationCertificateRevokedEvent.class)).isEmpty();
    }
}
