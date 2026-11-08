package com.red_escuela.backend.demo.infrastructure;

import com.red_escuela.backend.demo.DemoPersona;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DemoHttpClient {

    private final RestClient restClient;
    private final Map<String, String> tokensByUsername = new HashMap<>();

    public DemoHttpClient(int port) {
        this.restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    public JsonNode post(String username, String path, Object body) {
        return describingFailure("POST", path, () -> restClient.post().uri(path)
                .header("Authorization", "Bearer " + tokenFor(username))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body == null ? Map.of() : body)
                .retrieve()
                .body(JsonNode.class));
    }

    public JsonNode get(String username, String path) {
        return describingFailure("GET", path, () -> restClient.get().uri(path)
                .header("Authorization", "Bearer " + tokenFor(username))
                .retrieve()
                .body(JsonNode.class));
    }

    public JsonNode getOrNull(String username, String path) {
        try {
            return restClient.get().uri(path)
                    .header("Authorization", "Bearer " + tokenFor(username))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            return null;
        }
    }

    private JsonNode describingFailure(String method, String path, Supplier<JsonNode> call) {
        try {
            return call.get();
        } catch (RestClientResponseException failure) {
            throw new IllegalStateException(method + " " + path + " respondió " + failure.getStatusCode().value()
                    + ": " + failure.getResponseBodyAsString(), failure);
        }
    }

    public JsonNode getPublic(String path) {
        return restClient.get().uri(path).retrieve().body(JsonNode.class);
    }

    public JsonNode register(Object body) {
        return restClient.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private String tokenFor(String username) {
        return tokensByUsername.computeIfAbsent(username, this::login);
    }

    private String login(String username) {
        JsonNode response = restClient.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", username, "password", DemoPersona.PASSWORD))
                .retrieve()
                .body(JsonNode.class);
        return response.get("accessToken").asString("");
    }
}
