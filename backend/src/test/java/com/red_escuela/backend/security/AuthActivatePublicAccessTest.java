package com.red_escuela.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthActivatePublicAccessTest extends AuthorizationMatrixSupport {

    private static final String UNKNOWN_TOKEN_BODY =
            "{\"token\":\"unknown-token-that-never-existed\",\"newPassword\":\"Valid-Passw0rd-123\"}";

    private MockHttpServletResponse activate(String bearerToken) throws Exception {
        MockHttpServletRequestBuilder request = post("/auth/activate")
                .contentType(MediaType.APPLICATION_JSON).content(UNKNOWN_TOKEN_BODY);
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private String normalized(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString().replaceAll("\"requestId\":\"[^\"]*\"", "");
    }

    @Test
    void anonymousCallerWithUnknownTokenGetsBadRequestNotAuthenticationFailure() throws Exception {
        MockHttpServletResponse response = activate(null);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("\"retryable\":false");
    }

    @Test
    void everyRoleGetsTheSameAnswerAsAnonymousWhenTheTokenIsUnknown() throws Exception {
        String anonymousBody = normalized(activate(null));

        for (MatrixRole role : MatrixRole.values()) {
            MockHttpServletResponse response = activate(fixtures.tokenFor(role));

            assertThat(response.getStatus()).as("rol %s", role).isEqualTo(400);
            assertThat(normalized(response)).as("rol %s", role).isEqualTo(anonymousBody);
        }
    }

    @Test
    void malformedBearerTokenDoesNotBlockThePublicEndpoint() throws Exception {
        MockHttpServletResponse response = activate("not-a-jwt");

        assertThat(response.getStatus()).isNotIn(401, 403);
    }
}
