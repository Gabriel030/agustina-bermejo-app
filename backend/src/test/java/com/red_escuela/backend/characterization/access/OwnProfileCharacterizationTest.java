package com.red_escuela.backend.characterization.access;

import com.red_escuela.backend.identity.dto.AuthResponse;
import com.red_escuela.backend.identity.dto.ChangePasswordRequest;
import com.red_escuela.backend.identity.dto.RefreshRequest;
import com.red_escuela.backend.identity.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.Person;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OwnProfileCharacterizationTest extends AccessCharacterizationSupport {

    private static final String NEW_PASSWORD = "NuevaClave456";

    private org.springframework.test.web.servlet.ResultActions changePassword(Person person, String current, String next)
            throws Exception {
        return mockMvc.perform(put("/me/password")
                .header("Authorization", bearer(person))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangePasswordRequest(current, next))));
    }

    @Test
    void ownProfileEditIsVisibleInTheNextGetAndTheDniIsAlsoEditable() throws Exception {
        Person person = persistPerson("access.profile1", "70000041", "ALUMNO");
        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest("nuevo.mail@example.com", "1155556666",
                null, "99999999", null, "Calle Nueva", "10", null, "Ciudad", null, null, null);

        mockMvc.perform(put("/me")
                        .header("Authorization", bearer(person))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/me").header("Authorization", bearer(person)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nuevo.mail@example.com"))
                .andExpect(jsonPath("$.dni").value("99999999"));
    }

    @Test
    void getOwnProfileWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/me")).andExpect(status().is4xxClientError());
    }

    @Test
    void changingThePasswordRevokesSessionsAndSwitchesTheAcceptedPassword() throws Exception {
        Person person = persistPerson("access.profile2", "70000042", "ALUMNO");
        AuthResponse previousSession = loginSuccessfully("access.profile2", PASSWORD);

        changePassword(person, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login("access.profile2", PASSWORD).andExpect(status().isUnauthorized());
        login("access.profile2", NEW_PASSWORD).andExpect(status().isOk());
        postJson("/auth/refresh", new RefreshRequest(previousSession.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingThePasswordWithAWrongCurrentPasswordReturns400AndKeepsTheOldOne() throws Exception {
        Person person = persistPerson("access.profile3", "70000043", "ALUMNO");

        changePassword(person, "Equivocada1", NEW_PASSWORD).andExpect(status().isBadRequest());

        login("access.profile3", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changingThePasswordToOneThatBreaksThePolicyReturns400() throws Exception {
        Person person = persistPerson("access.profile4", "70000044", "ALUMNO");

        changePassword(person, PASSWORD, "corta").andExpect(status().isBadRequest());
        changePassword(person, PASSWORD, "sinmayuscula1").andExpect(status().isBadRequest());
        changePassword(person, PASSWORD, "SinNumeroAlguno").andExpect(status().isBadRequest());

        login("access.profile4", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void changingThePasswordWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(put("/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequest(PASSWORD, NEW_PASSWORD))))
                .andExpect(status().is4xxClientError());
    }
}
