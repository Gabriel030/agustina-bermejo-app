package com.red_escuela.backend.billing.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceCatalog;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@Tag("D-214")
@Tag("D-240")
@SpringBootTest
@ActiveProfiles("test")
class PriceChangeConcurrencyIT extends AbstractIntegrationTest {
    private static final int ROUNDS = 12;

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
    private OfferingPriceCatalog offeringPriceCatalog;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private Person administrative(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) username.hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ADMINISTRATIVO").orElseThrow();
        person.getRoles().add(role);
        return personRepository.save(person);
    }

    private long createProgramPricedAt(String token, String name, int monthlyPrice) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("name", name, "totalYears", 3, "totalCreditsRequired", 100,
                "price", java.util.Map.of("amount", monthlyPrice, "currency", "ARS")));
        String response = mockMvc.perform(post("/programs").contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token).content(body))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private int putPrice(String token, long programId, int monthlyPrice) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("price", java.util.Map.of("amount", monthlyPrice, "currency", "ARS")));
        return mockMvc.perform(put("/programs/{id}", programId).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token).content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void twoSimultaneousPriceChangesOnTheSameDayNeverAnswerWithAServerError() throws Exception {
        Person actor = administrative("price-race-http");
        String token = jwtService.generateAccessToken(actor);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long programId = createProgramPricedAt(token, "Carrera carrera de precios " + round + "-" + System.nanoTime(), 100000);
                CyclicBarrier barrier = new CyclicBarrier(2);
                List<Future<Integer>> calls = new ArrayList<>();
                for (int price : new int[]{120000, 130000}) {
                    calls.add(executor.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return putPrice(token, programId, price);
                    }));
                }
                for (Future<Integer> call : calls) {
                    statuses.add(call.get(60, TimeUnit.SECONDS));
                }
                assertThat(jdbc.queryForObject("select count(*) from price_version v join price p on p.id = v.price_id where p.program_id = ? "
                        + "and p.fee_type = 'MONTHLY_INSTALLMENT' and v.status = 'EFFECTIVE'", Long.class, programId)).isEqualTo(1L);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(statuses).hasSize(ROUNDS * 2).allMatch(status -> status == 200 || status == 409);
    }

    @Test
    void thePriceChangeThatLosesTheRaceForTheSameDayIsAConflictWithItsOwnCode() throws Exception {
        Person actor = administrative("price-race-held");
        String token = jwtService.generateAccessToken(actor);
        long programId = createProgramPricedAt(token, "Carrera con precio retenido " + System.nanoTime(), 100000);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstHoldsTheRow = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                offeringPriceCatalog.adjustOfferingPrice(OfferingReference.ofProgram(programId), OfferingFee.MONTHLY_INSTALLMENT, Money.of(new BigDecimal("120000"), Currency.ARS), actor.getId());
                firstHoldsTheRow.countDown();
                try {
                    releaseFirst.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(firstHoldsTheRow.await(30, TimeUnit.SECONDS)).isTrue();
            Future<String[]> second = executor.submit(() -> {
                String body = objectMapper.writeValueAsString(java.util.Map.of("price", java.util.Map.of("amount", 130000, "currency", "ARS")));
                var result = mockMvc.perform(put("/programs/{id}", programId).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + token).content(body))
                        .andReturn().getResponse();
                return new String[]{String.valueOf(result.getStatus()), result.getContentAsString()};
            });
            long deadline = System.currentTimeMillis() + 30000;
            while (jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Long.class) == 0
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            releaseFirst.countDown();
            first.get(30, TimeUnit.SECONDS);
            String[] outcome = second.get(30, TimeUnit.SECONDS);

            assertThat(outcome[0]).isEqualTo("409");
            assertThat(outcome[1]).contains("PRICE_CHANGED_CONCURRENTLY");
            assertThat(jdbc.queryForObject("select amount from price_version v join price p on p.id = v.price_id where p.program_id = ? "
                    + "and p.fee_type = 'MONTHLY_INSTALLMENT' and v.status = 'EFFECTIVE'", BigDecimal.class, programId)).isEqualByComparingTo("120000");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }
}
