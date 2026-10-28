package com.red_escuela.backend.billing.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag("D-116")
@Tag("D-211")
@SpringBootTest
@ActiveProfiles("test")
@SlowTest(reason = "Races concurrent price proposals from several threads against the same row lock")
class PriceProposalConcurrencyIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 8, 20);
    private static final LocalDate START = LocalDate.of(2027, 9, 1);
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
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private Person firstAdministrative;
    private Person secondAdministrative;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        firstAdministrative = administrative("proposal-race-first");
        secondAdministrative = administrative("proposal-race-second");
    }

    private Person administrative(String prefix) {
        return transactionTemplate.execute(status -> {
            String username = prefix + "." + System.nanoTime();
            Person person = new Person();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName("ADMINISTRATIVO").orElseThrow());
            return personRepository.save(person);
        });
    }

    private long createProgram(String token, int round) throws Exception {
        String response = mockMvc.perform(post("/programs").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Carrera de carrera de propuestas " + round + "-" + System.nanoTime(), "totalYears", 3,
                                "totalCreditsRequired", 100, "price", Map.of("amount", 100000, "currency", "ARS"),
                                "enrollmentFeeAmount", Map.of("amount", 50000, "currency", "ARS")))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private int propose(String token, long priceId, long amount) throws Exception {
        String body = "{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"effectiveFrom\":\"" + START + "\",\"changeReason\":\"aumento\"}";
        return mockMvc.perform(post("/prices/{id}/versions", priceId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private long priceId(long programId, String feeType) {
        return jdbc.queryForObject("select id from price where program_id = ? and fee_type = ?", Long.class, programId, feeType);
    }

    private List<Integer> race(ExecutorService executor, long firstPriceId, long secondPriceId) throws Exception {
        String firstToken = jwtService.generateAccessToken(firstAdministrative);
        String secondToken = jwtService.generateAccessToken(secondAdministrative);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Future<Integer> first = executor.submit(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            return propose(firstToken, firstPriceId, 120000);
        });
        Future<Integer> second = executor.submit(() -> {
            barrier.await(10, TimeUnit.SECONDS);
            return propose(secondToken, secondPriceId, 130000);
        });
        return List.of(first.get(60, TimeUnit.SECONDS), second.get(60, TimeUnit.SECONDS));
    }

    @Test
    void twoSimultaneousProposalsOnTheSamePriceLeaveExactlyOneProposalAndOneAuthorizationRequest() throws Exception {
        String token = jwtService.generateAccessToken(firstAdministrative);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                long programId = createProgram(token, round);
                long priceId = priceId(programId, "MONTHLY_INSTALLMENT");

                List<Integer> statuses = race(executor, priceId, priceId);

                assertThat(statuses).containsExactlyInAnyOrder(202, 409);
                assertThat(jdbc.queryForObject("select count(*) from price_version where price_id = ? and status = 'PROPOSED'", Long.class, priceId)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from authorization_request r join price_version v on v.authorization_request_id = r.id "
                        + "where v.price_id = ? and r.status = 'PENDING'", Long.class, priceId)).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from authorization_request where operation_type = 'CHANGE_PRICE' and requested_by_id in (?, ?) "
                        + "and (operation_payload ->> 'priceId')::bigint = ?", Long.class, firstAdministrative.getId(), secondAdministrative.getId(), priceId)).isEqualTo(1L);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void twoSimultaneousProposalsOnDifferentPricesOfTheSameOfferingCoexist() throws Exception {
        String token = jwtService.generateAccessToken(firstAdministrative);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            long programId = createProgram(token, 0);
            List<Integer> statuses = new ArrayList<>(race(executor, priceId(programId, "MONTHLY_INSTALLMENT"), priceId(programId, "ENROLLMENT_FEE")));

            assertThat(statuses).containsExactly(202, 202);
            assertThat(jdbc.queryForObject("select count(*) from price_version v join price p on p.id = v.price_id where p.program_id = ? and v.status = 'PROPOSED'",
                    Long.class, programId)).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
        }
    }
}
