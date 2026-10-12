package com.red_escuela.backend.shared.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.UnsupportedCurrencyException;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Component
public class ValueObjectJacksonModule extends SimpleModule {

    private static final String AMOUNT = "amount";
    private static final String CURRENCY = "currency";
    private static final String TYPE = "type";
    private static final String NOMINAL = "NOMINAL";
    private static final String PROPORTIONAL = "PROPORTIONAL";
    private static final String RATE_PERCENT = "ratePercent";

    public ValueObjectJacksonModule() {
        super("ValueObjectJacksonModule");
        addSerializer(Money.class, new MoneySerializer());
        addDeserializer(Money.class, new MoneyDeserializer());
        addSerializer(Percentage.class, new PercentageSerializer());
        addDeserializer(Percentage.class, new PercentageDeserializer());
        addSerializer(EmailAddress.class, new EmailAddressSerializer());
        addDeserializer(EmailAddress.class, new EmailAddressDeserializer());
        addSerializer(MonetaryAdjustment.class, new MonetaryAdjustmentSerializer());
        addDeserializer(MonetaryAdjustment.class, new MonetaryAdjustmentDeserializer());
    }

    private static void writePoints(JsonGenerator generator, Percentage percentage) throws IOException {
        generator.writeNumber(percentage.points().stripTrailingZeros().toPlainString());
    }

    private static JsonNode requiredField(JsonNode node, String field, DeserializationContext context, Class<?> target)
            throws IOException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return context.reportInputMismatch(target, "Missing field '%s'", field);
        }
        return value;
    }

    private static final class MoneySerializer extends JsonSerializer<Money> {
        @Override
        public void serialize(Money money, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeStartObject();
            generator.writeStringField(AMOUNT, money.amount().toPlainString());
            generator.writeStringField(CURRENCY, money.currency().name());
            generator.writeEndObject();
        }
    }

    private static final class MoneyDeserializer extends JsonDeserializer<Money> {
        @Override
        public Money deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = context.readTree(parser);
            BigDecimal amount = new BigDecimal(requiredField(node, AMOUNT, context, Money.class).asText());
            String currencyCode = requiredField(node, CURRENCY, context, Money.class).asText();
            Currency currency;
            try {
                currency = Currency.valueOf(currencyCode);
            } catch (IllegalArgumentException e) {
                throw new UnsupportedCurrencyException(currencyCode);
            }
            return Money.of(amount, currency);
        }
    }

    private static final class PercentageSerializer extends JsonSerializer<Percentage> {
        @Override
        public void serialize(Percentage percentage, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            writePoints(generator, percentage);
        }
    }

    private static final class PercentageDeserializer extends JsonDeserializer<Percentage> {
        @Override
        public Percentage deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return Percentage.ofPoints(parser.getDecimalValue());
        }
    }

    private static final class EmailAddressSerializer extends JsonSerializer<EmailAddress> {
        @Override
        public void serialize(EmailAddress emailAddress, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(emailAddress.value());
        }
    }

    private static final class EmailAddressDeserializer extends JsonDeserializer<EmailAddress> {
        @Override
        public EmailAddress deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return EmailAddress.of(parser.getValueAsString());
        }
    }

    private static final class MonetaryAdjustmentSerializer extends JsonSerializer<MonetaryAdjustment> {
        @Override
        public void serialize(MonetaryAdjustment adjustment, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeStartObject();
            switch (adjustment) {
                case MonetaryAdjustment.Nominal nominal -> {
                    generator.writeStringField(TYPE, NOMINAL);
                    provider.defaultSerializeField(AMOUNT, nominal.amount(), generator);
                }
                case MonetaryAdjustment.Proportional proportional -> {
                    generator.writeStringField(TYPE, PROPORTIONAL);
                    generator.writeFieldName(RATE_PERCENT);
                    writePoints(generator, proportional.rate());
                }
            }
            generator.writeEndObject();
        }
    }

    private static final class MonetaryAdjustmentDeserializer extends JsonDeserializer<MonetaryAdjustment> {
        @Override
        public MonetaryAdjustment deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = context.readTree(parser);
            String type = requiredField(node, TYPE, context, MonetaryAdjustment.class).asText();
            return switch (type) {
                case NOMINAL -> new MonetaryAdjustment.Nominal(
                        context.readTreeAsValue(requiredField(node, AMOUNT, context, MonetaryAdjustment.class), Money.class));
                case PROPORTIONAL -> new MonetaryAdjustment.Proportional(Percentage.ofPoints(ratePoints(node, context)));
                default -> context.reportInputMismatch(MonetaryAdjustment.class, "Unknown adjustment type '%s'", type);
            };
        }

        private static BigDecimal ratePoints(JsonNode node, DeserializationContext context) throws IOException {
            JsonNode rate = requiredField(node, RATE_PERCENT, context, MonetaryAdjustment.class);
            if (!rate.isNumber()) {
                return context.reportInputMismatch(MonetaryAdjustment.class, "Field '%s' must be a number", RATE_PERCENT);
            }
            return rate.decimalValue();
        }
    }
}
