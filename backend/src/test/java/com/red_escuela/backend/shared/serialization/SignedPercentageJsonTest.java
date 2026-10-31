package com.red_escuela.backend.shared.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.SignedPercentageWireSchemaCustomizer;
import com.red_escuela.backend.shared.domain.SignedPercentage;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SignedPercentageJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new ValueObjectJacksonModule());

    @Test
    void signedPercentageIsWrittenAsAPointsNumberKeepingTheSign() throws Exception {
        assertThat(MAPPER.writeValueAsString(SignedPercentage.ofPoints(new BigDecimal("-12.5")))).isEqualTo("-12.5");
        assertThat(MAPPER.writeValueAsString(SignedPercentage.ofPoints(new BigDecimal("250")))).isEqualTo("250");
        assertThat(MAPPER.writeValueAsString(SignedPercentage.ofPoints(BigDecimal.ZERO))).isEqualTo("0");
    }

    @Test
    void signedPercentageRoundTrips() throws Exception {
        SignedPercentage original = SignedPercentage.ofPoints(new BigDecimal("-11.11"));

        assertThat(MAPPER.readValue(MAPPER.writeValueAsString(original), SignedPercentage.class)).isEqualTo(original);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void customizerReplacesSignedPercentageReferencesWithPlainNumberAndDropsTheSchema() {
        Schema response = new ObjectSchema();
        response.addProperty("differencePercentage", new Schema().$ref("#/components/schemas/SignedPercentage"));
        response.addProperty("count", new NumberSchema());
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("DraftInvoiceGroupResponse", response).addSchemas("SignedPercentage", new ObjectSchema()));

        new SignedPercentageWireSchemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).doesNotContainKey("SignedPercentage");
        assertThat(((Schema) response.getProperties().get("differencePercentage")).getType()).isEqualTo("number");
        assertThat(((Schema) response.getProperties().get("differencePercentage")).get$ref()).isNull();
    }
}
