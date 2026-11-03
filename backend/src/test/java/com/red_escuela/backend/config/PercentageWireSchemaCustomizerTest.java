package com.red_escuela.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.serialization.ValueObjectJacksonModule;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PercentageWireSchemaCustomizerTest {

    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper().registerModule(new ValueObjectJacksonModule());
    private static final String REF = "#/components/schemas/Percentage";

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static OpenAPI customized() {
        Schema holder = new ObjectSchema();
        holder.addProperty("absencePercent", new Schema().$ref(REF));
        Schema series = new ObjectSchema();
        series.addProperty("values", new ArraySchema().items(new Schema().oneOf(new ArrayList<>(List.of(new Schema().$ref("#/components/schemas/Money"), new Schema().$ref(REF))))));
        OpenAPI openApi = new OpenAPI().components(new Components()
                .addSchemas("Percentage", new ObjectSchema())
                .addSchemas("Holder", holder)
                .addSchemas("Series", series));
        new PercentageWireSchemaCustomizer().customise(openApi);
        return openApi;
    }

    @Test
    void propertyReferencesBecomeNumbersAndTheObjectSchemaDisappears() {
        OpenAPI openApi = customized();
        Schema<?> property = (Schema<?>) openApi.getComponents().getSchemas().get("Holder").getProperties().get("absencePercent");
        assertThat(property.getType()).isEqualTo("number");
        assertThat(property.get$ref()).isNull();
        assertThat(property.getMinimum()).isEqualByComparingTo("0");
        assertThat(property.getMaximum()).isEqualByComparingTo("100");
        assertThat(property.getDescription()).contains("puntos");
        assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("Percentage");
    }

    @Test
    void oneOfEntriesAreReplacedAndOtherReferencesKept() {
        Schema<?> values = (Schema<?>) customized().getComponents().getSchemas().get("Series").getProperties().get("values");
        List<Schema> options = values.getItems().getOneOf();
        assertThat(options.get(0).get$ref()).isEqualTo("#/components/schemas/Money");
        assertThat(options.get(1).getType()).isEqualTo("number");
    }

    @Test
    void serializedPercentageIsANumberInsideTheDeclaredRange() throws Exception {
        JsonNode wire = WIRE_MAPPER.readTree(WIRE_MAPPER.writeValueAsString(Percentage.ofPoints(new BigDecimal("12.5"))));
        assertThat(wire.isNumber()).isTrue();
        assertThat(wire.decimalValue()).isEqualByComparingTo("12.5");
    }

    @Test
    void numberRequestBodyDeserializesToPercentage() throws Exception {
        Percentage parsed = WIRE_MAPPER.readValue("12.5", Percentage.class);
        assertThat(parsed.points()).isEqualByComparingTo("12.5");
    }
}
