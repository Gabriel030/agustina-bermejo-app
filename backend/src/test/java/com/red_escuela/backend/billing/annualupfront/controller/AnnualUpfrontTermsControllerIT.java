package com.red_escuela.backend.billing.annualupfront.controller;

import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Tag("D-236")
@Tag("D-441")
class AnnualUpfrontTermsControllerIT extends AbstractAnnualUpfrontRequestIT {
    private static final String NEW_TEXT = "Segunda redacción de la condición del pago anual: no se devuelve ni se acredita nada tras la baja.";

    @AfterEach
    void removePublishedTermsVersions() {
        jdbc.update("delete from annual_upfront_terms_version where version_number > 1");
    }

    @Test
    void theDirectorPublishesANewVersionAndTheCurrentOneChangesWhilePreviousTextStaysUntouched() throws Exception {
        String firstText = jdbc.queryForObject("select text from annual_upfront_terms_version where version_number = 1", String.class);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 2, 1, 9, 30));

        publish(NEW_TEXT, directorToken()).andExpect(status().isCreated()).andExpect(jsonPath("$.versionNumber").value(2)).andExpect(jsonPath("$.version").value("v2"))
                .andExpect(jsonPath("$.publishedByPersonId").value(director.getId())).andExpect(jsonPath("$.publishedAt").value("2027-02-01T09:30:00"));

        mockMvc.perform(get("/annual-upfront-terms/current").header("Authorization", "Bearer " + studentToken())).andExpect(status().isOk()).andExpect(jsonPath("$.version").value("v2"))
                .andExpect(jsonPath("$.text").value(NEW_TEXT)).andExpect(jsonPath("$.current").value(true));
        mockMvc.perform(get("/annual-upfront-terms/versions/1").header("Authorization", "Bearer " + studentToken())).andExpect(status().isOk()).andExpect(jsonPath("$.text").value(firstText))
                .andExpect(jsonPath("$.current").value(false));
        mockMvc.perform(get("/annual-upfront-terms/versions").header("Authorization", "Bearer " + directorToken())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value("v2")).andExpect(jsonPath("$[0].current").value(true)).andExpect(jsonPath("$[1].version").value("v1"))
                .andExpect(jsonPath("$[1].current").value(false));
        assertThat(jdbc.queryForObject("select text from annual_upfront_terms_version where version_number = 1", String.class)).isEqualTo(firstText);
    }

    @Test
    void aTextOutsideTheAllowedLengthIsRejectedAndNothingIsPublished() throws Exception {
        publish("x".repeat(49), directorToken()).andExpect(status().isBadRequest());
        publish("x".repeat(20001), directorToken()).andExpect(status().isBadRequest());
        publish("x".repeat(50), directorToken()).andExpect(status().isCreated());
        publish("y".repeat(20000), directorToken()).andExpect(status().isCreated());

        assertThat(count("select count(*) from annual_upfront_terms_version")).isEqualTo(3);
    }

    @Test
    void onlyTheDirectorPublishesAndListsVersions() throws Exception {
        publish(NEW_TEXT, studentToken()).andExpect(status().isForbidden());
        mockMvc.perform(get("/annual-upfront-terms/versions").header("Authorization", "Bearer " + studentToken())).andExpect(status().isForbidden());

        assertThat(count("select count(*) from annual_upfront_terms_version")).isEqualTo(1);
    }

    @Test
    void anUnknownVersionIsNotFound() throws Exception {
        mockMvc.perform(get("/annual-upfront-terms/versions/99").header("Authorization", "Bearer " + studentToken())).andExpect(status().isNotFound());
    }

    @Test
    void requestingWithTheCurrentVersionStoresItsExactTextAndNumber() throws Exception {
        publish(NEW_TEXT, directorToken()).andExpect(status().isCreated());

        request(true, "v2", 201);

        assertThat(count("select count(*) from annual_upfront_request where accepted_terms_version = 'v2' and accepted_terms_text = '" + NEW_TEXT + "'")).isEqualTo(1);
    }

    @Test
    void requestingWithAnOutdatedVersionGetsTheOutdatedConflictAndCreatesNothing() throws Exception {
        publish(NEW_TEXT, directorToken()).andExpect(status().isCreated());
        long invoicesBefore = count("select count(*) from invoice where invoice_type = 'ANNUAL_UPFRONT'");

        String body = request(true, "v1", 409).getResponse().getContentAsString();

        assertThat(body).contains("ANNUAL_UPFRONT_TERMS_OUTDATED");
        assertThat(count("select count(*) from invoice where invoice_type = 'ANNUAL_UPFRONT'")).isEqualTo(invoicesBefore);
        assertThat(count("select count(*) from annual_upfront_request")).isZero();
    }

    @Test
    void requestingWithoutAcceptingOrWithAnUnknownVersionKeepsTheNotAcceptedRejection() throws Exception {
        publish(NEW_TEXT, directorToken()).andExpect(status().isCreated());

        assertThat(request(false, "v2", 400).getResponse().getContentAsString()).contains("ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED");
        assertThat(request(true, "v3", 400).getResponse().getContentAsString()).contains("ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED");
        assertThat(request(true, "not-a-version", 400).getResponse().getContentAsString()).contains("ANNUAL_UPFRONT_TERMS_NOT_ACCEPTED");
        assertThat(count("select count(*) from annual_upfront_request")).isZero();
    }

    private ResultActions publish(String text, String token) throws Exception {
        return mockMvc.perform(post("/annual-upfront-terms/versions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("text", text))));
    }

    private String directorToken() {
        return jwtService.generateAccessToken(director);
    }
}
