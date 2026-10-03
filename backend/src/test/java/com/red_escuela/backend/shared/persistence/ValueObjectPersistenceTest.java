package com.red_escuela.backend.shared.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.DateRange;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ValueObjectPersistenceTest extends AbstractIntegrationTest {

    @Autowired
    private TestValueObjectHolderRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private TestValueObjectHolder reload(TestValueObjectHolder holder) {
        entityManager.flush();
        entityManager.clear();
        return repository.findById(holder.getId()).orElseThrow();
    }

    private Map<String, Object> rawRow(Long id) {
        entityManager.flush();
        return jdbcTemplate.queryForMap(
                "select price_amount, price_currency, discount_rate, contact_email, adjustment::text as adjustment, "
                        + "period_start, period_end from test_value_object_holder where id = ?", id);
    }

    @Test
    void moneyPercentageEmailAndRangeRoundTripThroughPostgres() {
        TestValueObjectHolder saved = repository.save(TestValueObjectHolder.builder()
                .price(Money.of(new BigDecimal("48000.005"), Currency.ARS))
                .discountRate(Percentage.ofPoints(new BigDecimal("12.5")))
                .contactEmail(EmailAddress.of(" Ana.Perez@Mail.com "))
                .period(new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31)))
                .build());

        TestValueObjectHolder loaded = reload(saved);

        assertThat(loaded.getPrice()).isEqualTo(Money.of(new BigDecimal("48000.01"), Currency.ARS));
        assertThat(loaded.getDiscountRate()).isEqualTo(Percentage.ofFraction(new BigDecimal("0.125")));
        assertThat(loaded.getContactEmail()).isEqualTo(EmailAddress.of("ana.perez@mail.com"));
        assertThat(loaded.getPeriod()).isEqualTo(new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31)));
    }

    @Test
    void columnsHoldTheExpectedRawValues() {
        TestValueObjectHolder saved = repository.save(TestValueObjectHolder.builder()
                .price(Money.of(new BigDecimal("150.5"), Currency.USD))
                .discountRate(Percentage.ratio(1, 3).orElseThrow())
                .contactEmail(EmailAddress.of("ANA@MAIL.COM"))
                .build());

        Map<String, Object> row = rawRow(saved.getId());

        assertThat(row.get("price_amount")).isEqualTo(new BigDecimal("150.50"));
        assertThat(row.get("price_currency")).isEqualTo("USD");
        assertThat(row.get("discount_rate")).isEqualTo(new BigDecimal("0.333333"));
        assertThat(row.get("contact_email")).isEqualTo("ana@mail.com");
    }

    @Test
    void nominalAdjustmentIsStoredAsJsonbAndReadBack() throws Exception {
        MonetaryAdjustment nominal = new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("5000"), Currency.ARS));
        TestValueObjectHolder saved = repository.save(TestValueObjectHolder.builder().adjustment(nominal).build());

        JsonNode stored = objectMapper.readTree((String) rawRow(saved.getId()).get("adjustment"));
        assertThat(stored).isEqualTo(objectMapper.readTree("{\"type\":\"NOMINAL\",\"amount\":\"5000.00\",\"currency\":\"ARS\"}"));
        assertThat(reload(saved).getAdjustment()).isEqualTo(nominal);
    }

    @Test
    void proportionalAdjustmentIsStoredAsJsonbAndReadBack() throws Exception {
        MonetaryAdjustment proportional = new MonetaryAdjustment.Proportional(Percentage.ofPoints(new BigDecimal("10")));
        TestValueObjectHolder saved = repository.save(TestValueObjectHolder.builder().adjustment(proportional).build());

        JsonNode stored = objectMapper.readTree((String) rawRow(saved.getId()).get("adjustment"));
        assertThat(stored).isEqualTo(objectMapper.readTree("{\"type\":\"PROPORTIONAL\",\"fraction\":\"0.100000\"}"));
        assertThat(reload(saved).getAdjustment()).isEqualTo(proportional);
    }

    @Test
    void absentValueObjectsAreStoredAsNullAndReadBackAsNull() {
        TestValueObjectHolder saved = repository.save(TestValueObjectHolder.builder().build());

        Map<String, Object> row = rawRow(saved.getId());
        assertThat(row.values()).containsOnlyNulls();

        TestValueObjectHolder loaded = reload(saved);
        assertThat(loaded.getPrice()).isNull();
        assertThat(loaded.getDiscountRate()).isNull();
        assertThat(loaded.getContactEmail()).isNull();
        assertThat(loaded.getAdjustment()).isNull();
        assertThat(loaded.getPeriod()).isNull();
    }
}
