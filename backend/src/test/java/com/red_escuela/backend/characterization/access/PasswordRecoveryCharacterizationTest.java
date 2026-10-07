package com.red_escuela.backend.characterization.access;

import com.red_escuela.backend.identity.dto.AuthResponse;
import com.red_escuela.backend.identity.dto.ForgotPasswordRequest;
import com.red_escuela.backend.identity.dto.RefreshRequest;
import com.red_escuela.backend.identity.dto.ResetPasswordRequest;
import com.red_escuela.backend.identity.dto.VerifyResetCodeRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.PasswordResetCodeIssuedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class PasswordRecoveryCharacterizationTest extends AccessCharacterizationSupport {

    private static final String NEW_PASSWORD = "NuevaClave456";

    @Autowired
    private ApplicationEvents applicationEvents;

    private String requestCodeFor(Person person) throws Exception {
        postJson("/auth/forgot-password", new ForgotPasswordRequest(person.emailValue()))
                .andExpect(status().isAccepted());
        return applicationEvents.stream(PasswordResetCodeIssuedEvent.class)
                .filter(event -> event.personId().equals(person.getId()))
                .reduce((first, second) -> second)
                .orElseThrow()
                .code();
    }

    private String exchangeFor(Person person, String code) throws Exception {
        String body = postJson("/auth/verify-reset-code", new VerifyResetCodeRequest(person.emailValue(), code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("resetToken").asText();
    }

    @Test
    void passwordRecoveryFlowRequestsCodeVerifiesItAndChangesThePassword() throws Exception {
        Person person = persistPerson("access.reset1", "70000011", "ALUMNO");
        AuthResponse previousSession = loginSuccessfully("access.reset1", PASSWORD);

        String code = requestCodeFor(person);
        assertThat(code).matches("\\d{6}");

        String resetToken = exchangeFor(person, code);

        postJson("/auth/reset-password", new ResetPasswordRequest(resetToken, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        login("access.reset1", PASSWORD).andExpect(status().isUnauthorized());
        login("access.reset1", NEW_PASSWORD).andExpect(status().isOk());
        postJson("/auth/refresh", new RefreshRequest(previousSession.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordForUnknownEmailReturns202WithoutIssuingAnyCode() throws Exception {
        postJson("/auth/forgot-password", new ForgotPasswordRequest("ghost@example.com"))
                .andExpect(status().isAccepted());

        assertThat(applicationEvents.stream(PasswordResetCodeIssuedEvent.class).count()).isZero();
    }

    @Test
    void resetCodeCanBeVerifiedOnlyOnce() throws Exception {
        Person person = persistPerson("access.reset2", "70000012", "ALUMNO");
        String code = requestCodeFor(person);
        exchangeFor(person, code);

        postJson("/auth/verify-reset-code", new VerifyResetCodeRequest(person.emailValue(), code))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exchangeTokenCannotBeReusedAfterResettingThePassword() throws Exception {
        Person person = persistPerson("access.reset3", "70000013", "ALUMNO");
        String resetToken = exchangeFor(person, requestCodeFor(person));

        postJson("/auth/reset-password", new ResetPasswordRequest(resetToken, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        postJson("/auth/reset-password", new ResetPasswordRequest(resetToken, "OtraClave789"))
                .andExpect(status().isUnauthorized());
        login("access.reset3", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void fifthWrongCodeBurnsTheTokenSoTheCorrectCodeNoLongerWorks() throws Exception {
        Person person = persistPerson("access.reset4", "70000014", "ALUMNO");
        String code = requestCodeFor(person);
        String wrongCode = "000000".equals(code) ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            postJson("/auth/verify-reset-code", new VerifyResetCodeRequest(person.emailValue(), wrongCode))
                    .andExpect(status().isUnauthorized());
        }

        postJson("/auth/verify-reset-code", new VerifyResetCodeRequest(person.emailValue(), code))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyResetCodeForUnknownEmailReturns401() throws Exception {
        postJson("/auth/verify-reset-code", new VerifyResetCodeRequest("ghost@example.com", "123456"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPasswordRejectsAPasswordThatBreaksThePolicyWith400AndKeepsTheOldOne() throws Exception {
        Person person = persistPerson("access.reset5", "70000015", "ALUMNO");
        String resetToken = exchangeFor(person, requestCodeFor(person));

        postJson("/auth/reset-password", new ResetPasswordRequest(resetToken, "corta"))
                .andExpect(status().isBadRequest());
        postJson("/auth/reset-password", new ResetPasswordRequest(resetToken, "sinmayuscula1"))
                .andExpect(status().isBadRequest());

        login("access.reset5", PASSWORD).andExpect(status().isOk());
    }
}
