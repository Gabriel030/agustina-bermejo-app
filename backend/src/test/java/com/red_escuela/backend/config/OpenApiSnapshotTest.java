package com.red_escuela.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class OpenApiSnapshotTest extends AbstractIntegrationTest {

    private static final Path SNAPSHOT = Path.of("..", "contracts", "openapi.json");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatedSpecMatchesCommittedSnapshot() throws Exception {
        String generated = fetchNormalizedSpec();
        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, generated, StandardCharsets.UTF_8);
            return;
        }
        assertThat(SNAPSHOT)
                .as("contracts/openapi.json no existe: generalo con -Dopenapi.update=true")
                .exists();
        String committed = Files.readString(SNAPSHOT, StandardCharsets.UTF_8);
        if (!generated.equals(committed)) {
            fail("El spec OpenAPI cambió respecto de contracts/openapi.json: regenerá con "
                    + "./mvnw test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true y revisá el diff. "
                    + describeFirstDifference(committed, generated));
        }
    }

    private String describeFirstDifference(String committed, String generated) {
        String[] committedLines = committed.split("\n", -1);
        String[] generatedLines = generated.split("\n", -1);
        int shared = Math.min(committedLines.length, generatedLines.length);
        for (int line = 0; line < shared; line++) {
            if (!committedLines[line].equals(generatedLines[line])) {
                return "Primera diferencia en la línea " + (line + 1) + ": versionado [" + committedLines[line].strip()
                        + "] / generado [" + generatedLines[line].strip() + "]";
            }
        }
        return "Las longitudes difieren: versionado " + committedLines.length + " líneas, generado "
                + generatedLines.length;
    }

    @Test
    void generatedSpecIsDeterministic() throws Exception {
        String first = fetchNormalizedSpec();
        String second = fetchNormalizedSpec();
        assertThat(second).isEqualTo(first);

        Set<String> operationIds = new HashSet<>();
        for (Map.Entry<String, JsonNode> path : MAPPER.readTree(first).get("paths").properties()) {
            for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
                String operationId = operation.getValue().path("operationId").asText();
                assertThat(operationIds.add(operationId))
                        .as("operationId duplicado: %s", operationId)
                        .isTrue();
            }
        }
    }

    @Test
    void enumsArePublishedAsSchemaEnums() throws Exception {
        JsonNode spec = MAPPER.readTree(fetchNormalizedSpec());
        boolean anyEnum = false;
        for (Map.Entry<String, JsonNode> schema : spec.at("/components/schemas").properties()) {
            for (Map.Entry<String, JsonNode> property : schema.getValue().path("properties").properties()) {
                if (property.getValue().has("enum")) {
                    anyEnum = true;
                    assertThat(property.getValue().get("enum")).isNotEmpty();
                }
            }
        }
        assertThat(anyEnum).as("ninguna propiedad publica un enum").isTrue();
    }

    @Test
    @Tag("D-407")
    void theContractNoLongerExposesTheLegacyScholarshipRoutesNorTheirSchemas() throws Exception {
        JsonNode spec = MAPPER.readTree(fetchNormalizedSpec());

        for (Map.Entry<String, JsonNode> path : spec.get("paths").properties()) {
            assertThat(path.getKey()).as("ruta de becas legacy").doesNotEndWith("/scholarships");
        }
        assertThat(spec.at("/components/schemas").has("CreateScholarshipRequest")).isFalse();
        assertThat(spec.at("/components/schemas").has("ScholarshipResponse")).isFalse();
    }

    @Test
    void bearerAuthSecuritySchemeIsDeclared() throws Exception {
        JsonNode scheme = MAPPER.readTree(fetchNormalizedSpec()).at("/components/securitySchemes/bearerAuth");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.path("bearerFormat").asText()).isEqualTo("JWT");
    }

    private String fetchNormalizedSpec() throws Exception {
        String raw = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode tree = MAPPER.readTree(raw);
        ((ObjectNode) tree).remove("servers");
        Object sorted = MAPPER.convertValue(tree, Object.class);
        return MAPPER.writeValueAsString(sorted).replace("\r\n", "\n") + "\n";
    }
}
