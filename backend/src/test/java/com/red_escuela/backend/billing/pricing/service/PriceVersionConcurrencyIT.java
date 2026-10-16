package com.red_escuela.backend.billing.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import io.micrometer.core.instrument.MeterRegistry;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag("D-115")
@Tag("D-277")
@Tag("D-214")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SlowTest(reason = "Races real threads against Postgres row locks over several rounds")
class PriceVersionConcurrencyIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 8, 20);
    private static final int ROUNDS = 6;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PriceVersionActivator activator;
    @Autowired
    private PriceReader priceReader;
    @Autowired
    private MeterRegistry meterRegistry;

    private String adminToken;
    private String directorToken;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        jdbc.update("update price_version set status = 'CANCELLED', cancellation_reason = 'cleanup', cancelled_at = now() where status = 'SCHEDULED'");
        adminToken = jwtService.generateAccessToken(person("ADMINISTRATIVO"));
        directorToken = jwtService.generateAccessToken(person("DIRECTOR"));
    }

    @AfterEach
    void tearDown() {
        MutableClock.INSTANCE.reset();
    }

    private Person person(String role) {
        Person person = new Person();
        String username = role.toLowerCase() + ".race." + System.nanoTime();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) username.hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(addressRepository.save(new Address()));
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(role).orElseThrow());
        return personRepository.save(person);
    }

    private long createPrice() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Carrera de carreras " + System.nanoTime(), "totalYears", 3, "totalCreditsRequired", 100,
                "price", Map.of("amount", 100000, "currency", "ARS")));
        String response = mockMvc.perform(post("/programs").contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + adminToken).content(body))
                .andReturn().getResponse().getContentAsString();
        long programId = objectMapper.readTree(response).get("id").asLong();
        return jdbc.queryForObject("select id from price where program_id = ? and fee_type = 'MONTHLY_INSTALLMENT'", Long.class, programId);
    }

    private MockHttpServletResponse publish(long priceId, long amount, LocalDate effectiveFrom) throws Exception {
        String body = "{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"effectiveFrom\":\"" + effectiveFrom + "\",\"changeReason\":\"motivo\"}";
        return mockMvc.perform(post("/prices/{id}/versions", priceId).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private MockHttpServletResponse cancel(long versionId) throws Exception {
        return mockMvc.perform(post("/price-versions/{id}/cancellation", versionId).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"se posterga\"}")).andReturn().getResponse();
    }

    private long versionIdOf(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString()).get("id").asLong();
    }

    private <T> List<T> raceTwo(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barrier = new CyclicBarrier(2);
            List<Future<T>> calls = new ArrayList<>();
            for (Callable<T> task : List.of(first, second)) {
                calls.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> call : calls) {
                results.add(call.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private String statusOf(long versionId) {
        return jdbc.queryForObject("select status from price_version where id = ?", String.class, versionId);
    }

    private void assertConsistentHistory(long priceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select c.price_version_id, c.from_status, c.to_status from price_version_status_change c "
                + "join price_version v on v.id = c.price_version_id where v.price_id = ? order by c.price_version_id, c.id", priceId);
        Long currentVersion = null;
        String previousTo = null;
        for (Map<String, Object> row : rows) {
            Long version = ((Number) row.get("price_version_id")).longValue();
            if (!version.equals(currentVersion)) {
                currentVersion = version;
                previousTo = null;
            }
            assertThat((String) row.get("from_status")).as("history of version %s: %s", version, rows).isEqualTo(previousTo);
            previousTo = (String) row.get("to_status");
        }
        assertThat(count("select count(*) from (select price_version_id, to_status from price_version_status_change c join price_version v on v.id = c.price_version_id "
                + "where v.price_id = ? group by price_version_id, to_status having count(*) > 1) duplicated", priceId)).isZero();
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'EFFECTIVE'", priceId)).isLessThanOrEqualTo(1);
    }

    @Test
    void twoSimultaneousCancellationsOfTheSameVersionCancelItOnceAndTheOtherIsA409() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long priceId = createPrice();
            long versionId = versionIdOf(publish(priceId, 120000, TODAY.plusDays(30)));

            List<Integer> statuses = raceTwo(() -> cancel(versionId).getStatus(), () -> cancel(versionId).getStatus());

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(statusOf(versionId)).isEqualTo("CANCELLED");
            assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and to_status = 'CANCELLED'", versionId)).isEqualTo(1);
            assertConsistentHistory(priceId);
        }
    }

    @Test
    void aCancellationRacingAPublicationOfTheSameDateLeavesOneCancelledRowAndOneScheduledVersion() throws Exception {
        LocalDate date = TODAY.plusDays(30);
        for (int round = 0; round < ROUNDS; round++) {
            long priceId = createPrice();
            long versionId = versionIdOf(publish(priceId, 120000, date));

            List<Integer> statuses = raceTwo(() -> cancel(versionId).getStatus(), () -> publish(priceId, 125000, date).getStatus());

            assertThat(statuses.get(1)).isEqualTo(201);
            assertThat(statuses.get(0)).isIn(200, 409);
            assertThat(statusOf(versionId)).isEqualTo("CANCELLED");
            assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and to_status = 'CANCELLED'", versionId)).isEqualTo(1);
            assertThat(count("select count(*) from price_version where price_id = ? and status = 'SCHEDULED' and effective_from = ?", priceId, date)).isEqualTo(1);
            assertConsistentHistory(priceId);
        }
    }

    @Test
    void theActivationJobRacingAPublicationOfTheSameDayNeverCancelsAVersionThatAlreadyRuled() throws Exception {
        LocalDate dueDay = TODAY.plusDays(1);
        for (int round = 0; round < ROUNDS; round++) {
            MutableClock.INSTANCE.setDate(TODAY);
            long priceId = createPrice();
            long scheduledId = versionIdOf(publish(priceId, 120000, dueDay));
            MutableClock.INSTANCE.setDate(dueDay);

            List<Integer> statuses = raceTwo(() -> {
                activator.activateDueVersions();
                return 0;
            }, () -> publish(priceId, 125000, dueDay).getStatus());

            assertThat(statuses.get(1)).isEqualTo(201);
            assertThat(statusOf(scheduledId)).isEqualTo("SUPERSEDED");
            assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and to_status = 'CANCELLED'", scheduledId)).isZero();
            assertThat(count("select count(*) from price_version where price_id = ? and status = 'EFFECTIVE' and amount = 125000", priceId)).isEqualTo(1);
            assertConsistentHistory(priceId);
        }
    }

    @Test
    void twoPublicationsOfTheSameFutureDateLeaveTheSecondOneScheduledAndTheFirstCancelledWithTheAutomaticReason() throws Exception {
        LocalDate date = TODAY.plusDays(15);
        for (int round = 0; round < ROUNDS; round++) {
            long priceId = createPrice();

            List<Integer> statuses = raceTwo(() -> publish(priceId, 120000, date).getStatus(), () -> publish(priceId, 125000, date).getStatus());

            assertThat(statuses).containsExactly(201, 201);
            assertThat(count("select count(*) from price_version where price_id = ? and status = 'SCHEDULED' and effective_from = ?", priceId, date)).isEqualTo(1);
            assertThat(count("select count(*) from price_version where price_id = ? and status = 'CANCELLED' and cancellation_reason = ?", priceId,
                    "Reemplazada por otra versión con la misma fecha de inicio.")).isEqualTo(1);
            assertConsistentHistory(priceId);
        }
    }

    @Test
    void aPublicationForTheDayOfAScheduledVersionSupersedesItAfterActivatingItAndNeverCancelsItEvenBeforeTheJobRuns() throws Exception {
        LocalDate dueDay = TODAY.plusDays(1);
        long priceId = createPrice();
        long scheduledId = versionIdOf(publish(priceId, 120000, dueDay));
        MutableClock.INSTANCE.setDate(dueDay);

        assertThat(publish(priceId, 125000, dueDay).getStatus()).isEqualTo(201);

        assertThat(statusOf(scheduledId)).isEqualTo("SUPERSEDED");
        assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and to_status = 'CANCELLED'", scheduledId)).isZero();
        assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and event = 'REACH_START' and actor_person_id is not null",
                scheduledId)).isEqualTo(1);
        assertConsistentHistory(priceId);
        assertThat(activator.activateDueVersions().versionsActivated()).isZero();
    }

    @Test
    void oneFailingPriceDoesNotRollBackTheActivationOfTheOthersAndIsRetriedTheNextDay() throws Exception {
        LocalDate dueDay = TODAY.plusDays(1);
        long failingPrice = createPrice();
        long failingVersion = versionIdOf(publish(failingPrice, 120000, dueDay));
        long healthyPrice = createPrice();
        long healthyVersion = versionIdOf(publish(healthyPrice, 130000, dueDay));
        MutableClock.INSTANCE.setDate(dueDay);
        double failuresBefore = meterRegistry.counter("billing_price_version_activation_failed_total").count();
        jdbc.execute("create function test_reject_price_activation() returns trigger language plpgsql as $$ begin "
                + "raise exception 'activation rejected by test'; end; $$");
        jdbc.execute("create trigger trg_test_reject_price_activation before update on price_version for each row "
                + "when (new.id = " + failingVersion + " and new.status = 'EFFECTIVE') execute function test_reject_price_activation()");
        try {
            PriceActivationOutcome outcome = activator.activateDueVersions();

            assertThat(outcome.pricesFailed()).isEqualTo(1);
            assertThat(outcome.pricesActivated()).isEqualTo(1);
            assertThat(outcome.versionsActivated()).isEqualTo(1);
            assertThat(statusOf(healthyVersion)).isEqualTo("EFFECTIVE");
            assertThat(statusOf(failingVersion)).isEqualTo("SCHEDULED");
            assertThat(meterRegistry.counter("billing_price_version_activation_failed_total").count()).isEqualTo(failuresBefore + 1);
            assertThat(count("select count(*) from batch_execution_log where job_name = 'ActivateDuePriceVersionsJob' and status = 'PARTIAL_FAILURE' "
                    + "and failed_count = 1 and errors like ?", "%price=" + failingPrice + "%")).isEqualTo(1);
        } finally {
            jdbc.execute("drop trigger trg_test_reject_price_activation on price_version");
            jdbc.execute("drop function test_reject_price_activation()");
        }

        MutableClock.INSTANCE.setDate(dueDay.plusDays(1));
        PriceActivationOutcome retry = activator.activateDueVersions();

        assertThat(retry.pricesFailed()).isZero();
        assertThat(retry.versionsActivated()).isEqualTo(1);
        assertThat(statusOf(failingVersion)).isEqualTo("EFFECTIVE");
        assertConsistentHistory(failingPrice);
        assertConsistentHistory(healthyPrice);
    }

    @Test
    void activatingOneDayOfPricesWalksThemAllAndLeavesTheNextDayUntouched() throws Exception {
        LocalDate septemberFirst = LocalDate.of(2027, 9, 1);
        LocalDate octoberFirst = LocalDate.of(2027, 10, 1);
        long priceId = createPrice();
        long first = jdbc.queryForObject("select id from price_version where price_id = ? order by id limit 1", Long.class, priceId);
        long october = versionIdOf(publish(priceId, 120000, octoberFirst));
        long september = versionIdOf(publish(priceId, 110000, septemberFirst));
        OfferingReference offering = OfferingReference.ofProgram(jdbc.queryForObject("select program_id from price where id = ?", Long.class, priceId));

        assertThat(amountOn(offering, LocalDate.of(2027, 8, 31))).isEqualTo(100000);
        assertThat(amountOn(offering, LocalDate.of(2027, 9, 15))).isEqualTo(110000);
        assertThat(amountOn(offering, octoberFirst)).isEqualTo(120000);

        MutableClock.INSTANCE.setDate(septemberFirst);
        assertThat(activator.activateDueVersions().versionsActivated()).isEqualTo(1);
        assertThat(statusOf(september)).isEqualTo("EFFECTIVE");
        assertThat(statusOf(october)).isEqualTo("SCHEDULED");
        assertThat(statusOf(first)).isEqualTo("SUPERSEDED");
        assertThat(activator.activateDueVersions().versionsActivated()).isZero();

        MutableClock.INSTANCE.setDate(octoberFirst);
        assertThat(activator.activateDueVersions().versionsActivated()).isEqualTo(1);
        assertThat(statusOf(october)).isEqualTo("EFFECTIVE");
        assertThat(statusOf(september)).isEqualTo("SUPERSEDED");
        assertThat(activator.activateDueVersions().versionsActivated()).isZero();
        assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and event = 'REACH_START' and actor_process = 'PriceVersionActivation'",
                september)).isEqualTo(1);
        assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and event = 'SUPERSEDE' and actor_process = 'PriceVersionActivation'",
                first)).isEqualTo(1);
        assertThat(count("select count(*) from price_version_status_change where price_version_id in (?, ?) and event in ('REACH_START', 'SUPERSEDE') "
                + "and actor_process = 'PriceVersionActivation'", october, september)).isEqualTo(3);
        assertConsistentHistory(priceId);
    }

    private long amountOn(OfferingReference offering, LocalDate date) {
        return priceReader.requirePriceVersionEffectiveOn(offering, FeeType.MONTHLY_INSTALLMENT, date).finalAmount().value().amount().longValue();
    }
}
