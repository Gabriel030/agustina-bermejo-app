package com.red_escuela.backend.config.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

public class DemoApiClient {
    private static final String DEMO_PASSWORD = "Test1234";

    private final RestClient restClient;
    private final Map<String, String> tokensByUsername = new HashMap<>();

    public DemoApiClient(int port) {
        this.restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    public JsonNode post(String username, String path, Object body) {
        return restClient.post().uri(path)
                .header("Authorization", "Bearer " + tokenFor(username))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode postFile(String username, String path, String reason, String filename, byte[] content) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("reason", reason);
        parts.add("comprobante", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return restClient.post().uri(path)
                .header("Authorization", "Bearer " + tokenFor(username))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode get(String username, String path) {
        return restClient.get().uri(path)
                .header("Authorization", "Bearer " + tokenFor(username))
                .retrieve()
                .body(JsonNode.class);
    }

    private String tokenFor(String username) {
        return tokensByUsername.computeIfAbsent(username, this::login);
    }

    private String login(String username) {
        JsonNode response = restClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", DEMO_PASSWORD))
                .retrieve()
                .body(JsonNode.class);
        return response.get("accessToken").asText();
    }
}
