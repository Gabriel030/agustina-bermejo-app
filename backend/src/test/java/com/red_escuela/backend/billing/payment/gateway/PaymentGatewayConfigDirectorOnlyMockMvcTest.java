package com.red_escuela.backend.billing.payment.gateway;

import com.red_escuela.backend.billing.entity.PaymentGatewayConfigChange;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigChangeRepository;
import com.red_escuela.backend.billing.repository.PaymentGatewayConfigRepository;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentGatewayConfigDirectorOnlyMockMvcTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private PaymentGatewayConfigRepository configRepository;

    @Autowired
    private PaymentGatewayConfigChangeRepository changeRepository;

    private Person persistPerson(String username) {
        Address address = addressRepository.save(new Address());
        Person person = new Person();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) (username).hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private String bearerOf(String roleName) {
        Person person = persistPerson("config.mockmvc." + roleName.toLowerCase() + "." + System.nanoTime());
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        return "Bearer " + jwtService.generateAccessToken(personRepository.save(person));
    }

    private void putConfig(String token, String body, int expectedStatus) throws Exception {
        mockMvc.perform(put("/payment-config").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void directorCanUpdatePaymentConfig() throws Exception {
        putConfig(bearerOf("DIRECTOR"), "{\"mpEnabled\":true}", 200);

        assertThat(configRepository.findFirstByOrderByIdAsc().orElseThrow().isMpEnabled()).isTrue();
    }

    @Test
    void administrativeCannotUpdatePaymentConfig() throws Exception {
        boolean before = configRepository.findFirstByOrderByIdAsc().orElseThrow().isMpEnabled();

        putConfig(bearerOf("ADMINISTRATIVO"), "{\"mpEnabled\":" + !before + "}", 403);

        assertThat(configRepository.findFirstByOrderByIdAsc().orElseThrow().isMpEnabled()).isEqualTo(before);
        assertThat(changeRepository.findAll()).isEmpty();
    }

    @Test
    void teacherPreceptorAndStudentCannotUpdatePaymentConfig() throws Exception {
        putConfig(bearerOf("DOCENTE"), "{\"mpEnabled\":true}", 403);
        putConfig(bearerOf("PRECEPTOR"), "{\"mpEnabled\":true}", 403);
        putConfig(bearerOf("ALUMNO"), "{\"mpEnabled\":true}", 403);
    }

    @Test
    void administrativeKeepsReadAccessToPaymentConfig() throws Exception {
        mockMvc.perform(get("/payment-config").header("Authorization", bearerOf("ADMINISTRATIVO")))
                .andExpect(status().isOk());
    }

    @Test
    void updateRecordsWhoWhenAndBeforeAndAfterValues() throws Exception {
        String token = bearerOf("DIRECTOR");
        putConfig(token, "{\"mpEnabled\":false,\"bankAlias\":\"alias.uno\",\"bankCbu\":\"1111111111111111111111\",\"accountHolder\":\"Titular Uno\"}", 200);
        putConfig(token, "{\"mpEnabled\":true,\"bankAlias\":\"alias.dos\",\"bankCbu\":\"2222222222222222222222\",\"accountHolder\":\"Titular Dos\"}", 200);

        List<PaymentGatewayConfigChange> changes = changeRepository.findAllByOrderByChangedAtDescIdDesc();
        assertThat(changes).hasSize(2);
        PaymentGatewayConfigChange latest = changes.get(0);
        assertThat(latest.isMpEnabledBefore()).isFalse();
        assertThat(latest.isMpEnabledAfter()).isTrue();
        assertThat(latest.getBankAliasBefore()).isEqualTo("alias.uno");
        assertThat(latest.getBankAliasAfter()).isEqualTo("alias.dos");
        assertThat(latest.getBankCbuBefore()).isEqualTo("1111111111111111111111");
        assertThat(latest.getBankCbuAfter()).isEqualTo("2222222222222222222222");
        assertThat(latest.getAccountHolderBefore()).isEqualTo("Titular Uno");
        assertThat(latest.getAccountHolderAfter()).isEqualTo("Titular Dos");
        assertThat(latest.getChangedBy().getUsername()).startsWith("config.mockmvc.director.");
        assertThat(latest.getChangedAt()).isEqualTo(configRepository.findFirstByOrderByIdAsc().orElseThrow().getUpdatedAt());
    }

    @Test
    void updateWithoutDifferencesRecordsNoChange() throws Exception {
        String token = bearerOf("DIRECTOR");
        putConfig(token, "{\"bankAlias\":\"alias.uno\"}", 200);
        putConfig(token, "{\"bankAlias\":\"alias.uno\"}", 200);

        assertThat(changeRepository.findAll()).hasSize(1);
    }
}
