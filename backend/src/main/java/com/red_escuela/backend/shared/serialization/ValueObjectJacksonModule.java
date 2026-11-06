package com.red_escuela.backend.shared.serialization;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.UnsupportedCurrencyException;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.SignedPercentage;
import org.springframework.stereotype.Component;

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
        addSerializer(Grade.class, new GradeSerializer());
        addSerializer(Percentage.class, new PercentageSerializer());
        addDeserializer(Percentage.class, new PercentageDeserializer());
        addSerializer(SignedPercentage.class, new SignedPercentageSerializer());
        addDeserializer(SignedPercentage.class, new SignedPercentageDeserializer());
        addSerializer(EmailAddress.class, new EmailAddressSerializer());
        addDeserializer(EmailAddress.class, new EmailAddressDeserializer());
        addSerializer(MonetaryAdjustment.class, new MonetaryAdjustmentSerializer());
        addDeserializer(MonetaryAdjustment.class, new MonetaryAdjustmentDeserializer());
    }

    private static void writePoints(JsonGenerator generator, Percentage percentage) {
        generator.writeNumber(percentage.points().stripTrailingZeros().toPlainString());
    }

    private static JsonNode requiredField(JsonNode node, String field, DeserializationContext context, Class<?> target)
            {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return context.reportInputMismatch(target, "Missing field '%s'", field);
        }
        return value;
    }

    private static final class GradeSerializer extends ValueSerializer<Grade> {
        @Override
        public void serialize(Grade grade, JsonGenerator generator, SerializationContext provider) {
            generator.writeNumber(grade.value());
        }
    }

    private static final class MoneySerializer extends ValueSerializer<Money> {
        @Override
        public void serialize(Money money, JsonGenerator generator, SerializationContext provider) {
            generator.writeStartObject();
            generator.writeStringProperty(AMOUNT, money.amount().toPlainString());
            generator.writeStringProperty(CURRENCY, money.currency().name());
            generator.writeEndObject();
        }
    }

    private static final class MoneyDeserializer extends ValueDeserializer<Money> {
        @Override
        public Money deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode node = context.readTree(parser);
            BigDecimal amount = new BigDecimal(requiredField(node, AMOUNT, context, Money.class).asString());
            String currencyCode = requiredField(node, CURRENCY, context, Money.class).asString();
            Currency currency;
            try {
                currency = Currency.valueOf(currencyCode);
            } catch (IllegalArgumentException e) {
                throw new UnsupportedCurrencyException(currencyCode);
            }
            return Money.of(amount, currency);
        }
    }

    private static final class PercentageSerializer extends ValueSerializer<Percentage> {
        @Override
        public void serialize(Percentage percentage, JsonGenerator generator, SerializationContext provider)
                {
            writePoints(generator, percentage);
        }
    }

    private static final class PercentageDeserializer extends ValueDeserializer<Percentage> {
        @Override
        public Percentage deserialize(JsonParser parser, DeserializationContext context) {
            return Percentage.ofPoints(parser.getDecimalValue());
        }
    }

    private static final class SignedPercentageSerializer extends ValueSerializer<SignedPercentage> {
        @Override
        public void serialize(SignedPercentage percentage, JsonGenerator generator, SerializationContext provider)
                {
            generator.writeNumber(percentage.points().stripTrailingZeros().toPlainString());
        }
    }

    private static final class SignedPercentageDeserializer extends ValueDeserializer<SignedPercentage> {
        @Override
        public SignedPercentage deserialize(JsonParser parser, DeserializationContext context) {
            return SignedPercentage.ofPoints(parser.getDecimalValue());
        }
    }

    private static final class EmailAddressSerializer extends ValueSerializer<EmailAddress> {
        @Override
        public void serialize(EmailAddress emailAddress, JsonGenerator generator, SerializationContext provider)
                {
            generator.writeString(emailAddress.value());
        }
    }

    private static final class EmailAddressDeserializer extends ValueDeserializer<EmailAddress> {
        @Override
        public EmailAddress deserialize(JsonParser parser, DeserializationContext context) {
            return EmailAddress.of(parser.getValueAsString());
        }
    }

    private static final class MonetaryAdjustmentSerializer extends ValueSerializer<MonetaryAdjustment> {
        @Override
        public void serialize(MonetaryAdjustment adjustment, JsonGenerator generator, SerializationContext provider)
                {
            generator.writeStartObject();
            switch (adjustment) {
                case MonetaryAdjustment.Nominal nominal -> {
                    generator.writeStringProperty(TYPE, NOMINAL);
                    provider.defaultSerializeProperty(AMOUNT, nominal.amount(), generator);
                }
                case MonetaryAdjustment.Proportional proportional -> {
                    generator.writeStringProperty(TYPE, PROPORTIONAL);
                    generator.writeName(RATE_PERCENT);
                    writePoints(generator, proportional.rate());
                }
            }
            generator.writeEndObject();
        }
    }

    private static final class MonetaryAdjustmentDeserializer extends ValueDeserializer<MonetaryAdjustment> {
        @Override
        public MonetaryAdjustment deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode node = context.readTree(parser);
            String type = requiredField(node, TYPE, context, MonetaryAdjustment.class).asString();
            return switch (type) {
                case NOMINAL -> new MonetaryAdjustment.Nominal(
                        context.readTreeAsValue(requiredField(node, AMOUNT, context, MonetaryAdjustment.class), Money.class));
                case PROPORTIONAL -> new MonetaryAdjustment.Proportional(Percentage.ofPoints(ratePoints(node, context)));
                default -> context.reportInputMismatch(MonetaryAdjustment.class, "Unknown adjustment type '%s'", type);
            };
        }

        private static BigDecimal ratePoints(JsonNode node, DeserializationContext context) {
            JsonNode rate = requiredField(node, RATE_PERCENT, context, MonetaryAdjustment.class);
            if (!rate.isNumber()) {
                return context.reportInputMismatch(MonetaryAdjustment.class, "Field '%s' must be a number", RATE_PERCENT);
            }
            return rate.decimalValue();
        }
    }
}
