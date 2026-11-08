package com.red_escuela.backend.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RoleSmokeIT extends AuthorizationMatrixSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest(name = "{0}")
    @EnumSource(MatrixRole.class)
    void everyRoleGetsNoServerErrorAndJsonBodiesOnParameterlessGetEndpoints(MatrixRole role) throws Exception {
        Map<String, MappedEndpoint> endpointsByKey = mappedEndpoints().stream()
                .collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first));
        List<String> failures = new ArrayList<>();
        int visited = 0;

        for (AuthorizationMatrixRow row : AuthorizationMatrixFile.read()) {
            if (!row.key().startsWith("GET ") || row.key().contains("{")) {
                continue;
            }
            MappedEndpoint endpoint = endpointsByKey.get(row.key());
            assertThat(endpoint).as("mapping para %s", row.key()).isNotNull();
            visited++;
            var result = mockMvc.perform(requestFactory.build(endpoint, row.body(), fixtures.tokenFor(role))).andReturn();
            MockHttpServletResponse response = result.getResponse();
            String problem = problemWith(response);
            if (problem != null) {
                failures.add(role + " " + row.key() + " -> " + response.getStatus() + " " + problem
                        + " EX " + result.getResolvedException());
            }
        }

        assertThat(visited).as("GET sin variable de ruta recorridos").isGreaterThan(20);
        assertThat(failures).isEmpty();
    }

    private String problemWith(MockHttpServletResponse response) throws Exception {
        int status = response.getStatus();
        if (status >= 500) {
            return "error de servidor";
        }
        String body = response.getContentAsString();
        boolean jsonExpected = status >= 200 && status < 300 && status != 204 && !body.isEmpty();
        if (!jsonExpected) {
            return null;
        }
        String contentType = response.getContentType();
        if (contentType == null || !MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return "content-type no JSON: " + contentType;
        }
        try {
            JSON.readTree(body);
        } catch (Exception e) {
            return "cuerpo JSON invalido";
        }
        return null;
    }
}
