package com.red_escuela.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.serialization.ValueObjectJacksonModule;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyWireSchemaCustomizerTest {

    private static final ObjectMapper WIRE_MAPPER = new ObjectMapper().registerModule(new ValueObjectJacksonModule());
    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();
    private static final Path SNAPSHOT = Path.of("..", "contracts", "openapi.json");

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static JsonNode customizedMoneyProperties() {
        Schema inline = new ObjectSchema();
        inline.addProperty("amount", new NumberSchema());
        inline.addProperty("negative", new BooleanSchema());
        Schema money = new Schema();
        money.setAllOf(List.of(new Schema().$ref("#/components/schemas/MetricValue"), inline));
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("Money", money));
        new MoneyWireSchemaCustomizer().customise(openApi);
        return SCHEMA_MAPPER.valueToTree(inline).get("properties");
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static JsonNode wireOf(String amount, Currency currency) throws Exception {
        return WIRE_MAPPER.readTree(WIRE_MAPPER.writeValueAsString(Money.of(new BigDecimal(amount), currency)));
    }

    @Test
    void everySerializedMoneyPropertyIsDeclared() throws Exception {
        assertThat(fieldNames(customizedMoneyProperties()))
                .containsAll(fieldNames(wireOf("1500.5", Currency.ARS)));
    }

    @Test
    void serializedAmountIsAStringMatchingTheDeclaredPattern() throws Exception {
        JsonNode amountSchema = customizedMoneyProperties().get("amount");
        Pattern pattern = Pattern.compile(amountSchema.get("pattern").asText());

        assertThat(amountSchema.get("type").asText()).isEqualTo("string");
        for (String raw : List.of("0", "1500.5", "-20", "-0.01", "123456789.99")) {
            JsonNode amount = wireOf(raw, Currency.USD).get("amount");

            assertThat(amount.isTextual()).isTrue();
            assertThat(pattern.matcher(amount.asText()).matches()).isTrue();
        }
    }

    @Test
    void serializedCurrencyIsOneOfTheDeclaredEnumValues() throws Exception {
        JsonNode currencySchema = customizedMoneyProperties().get("currency");

        for (Currency currency : Currency.values()) {
            assertThat(currencySchema.get("enum")).extracting(JsonNode::asText)
                    .contains(wireOf("10", currency).get("currency").asText());
        }
        assertThat(currencySchema.get("enum")).hasSize(Currency.values().length);
    }

    @Test
    void emptyPaymentConfigurationFieldsAreNullableButStillRequired() throws Exception {
        JsonNode schema = SCHEMA_MAPPER.readTree(Files.readString(SNAPSHOT)).at("/components/schemas/PaymentGatewayConfigResponse");

        for (String field : List.of("qrImageUrl", "bankAlias", "bankCbu", "accountHolder")) {
            assertThat(schema.at("/properties/" + field + "/type")).extracting(JsonNode::asText).containsExactly("string", "null");
            assertThat(schema.get("required")).extracting(JsonNode::asText).contains(field);
        }
    }

    @Test
    void committedContractDescribesMoneyAsTheWireFormat() throws Exception {
        JsonNode committed = SCHEMA_MAPPER.readTree(Files.readString(SNAPSHOT)).at("/components/schemas/Money");
        JsonNode expected = customizedMoneyProperties();
        JsonNode committedProperties = committed.at("/allOf/1/properties");

        assertThat(committedProperties.at("/amount/type").asText()).isEqualTo("string");
        assertThat(committedProperties.at("/amount/pattern")).isEqualTo(expected.at("/amount/pattern"));
        assertThat(committedProperties.at("/currency/enum")).isEqualTo(expected.at("/currency/enum"));
        assertThat(fieldNames(committedProperties)).contains("amount", "currency");
    }
}
