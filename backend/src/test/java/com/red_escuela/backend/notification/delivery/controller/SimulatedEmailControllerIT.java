package com.red_escuela.backend.notification.delivery.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.service.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.service.NotificationDeliveryService;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"test", "demo"})
@Tag("c10")
class SimulatedEmailControllerIT extends AbstractIntegrationTest {
    private static final String PATH = "/notification-deliveries/simulated";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private NotificationDeliveryService deliveryService;

    private Person persistPerson(String roleName) {
        String username = (roleName == null ? "sin.rol" : roleName.toLowerCase()) + "." + System.nanoTime();
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setEmailVerified(true);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            if (roleName != null) {
                person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            }
            entityManager.persist(person);
            return person;
        });
    }

    private String tokenWithRole(String roleName) {
        return jwtService.generateAccessToken(persistPerson(roleName));
    }

    private Person queueMailForNewStudent(String subject) {
        Person student = persistPerson("ALUMNO");
        DeliveryEnvelope envelope = new DeliveryEnvelope("simulated:" + System.nanoTime(), DeliveryCategory.COLLECTION_REMINDER, "BEFORE_DUE_REMINDER", 3,
                student.getId(), "Invoice", 11L, LocalDate.of(2027, 3, 9));
        deliveryService.enqueue(new DeliveryRequest(envelope, NotificationType.COLLECTION_REMINDER, null,
                DeliveryContent.forEmail(subject, "Cuerpo del aviso", null), false));
        return student;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRATIVO", "DIRECTOR"})
    void staffSeesTheMailboxWithTheRecipientMasked(String role) throws Exception {
        Person student = queueMailForNewStudent("Asunto visible " + role);
        String fullAddress = student.getEmail().value();

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenWithRole(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].subject").value("Asunto visible " + role))
                .andExpect(jsonPath("$.items[0].status").value("QUEUED"))
                .andExpect(jsonPath("$.items[0].bodyText").value("Cuerpo del aviso"))
                .andExpect(jsonPath("$.items[0].maskedRecipient").value(containsString("***@example.com")))
                .andExpect(content().string(not(containsString(fullAddress))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALUMNO", "DOCENTE", "PRECEPTOR"})
    void rolesOutsideTheStaffCannotReadTheMailbox(String role) throws Exception {
        queueMailForNewStudent("Asunto ajeno");

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenWithRole(role)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Asunto ajeno"))));
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isForbidden());
    }

    @Test
    void thePageSizeIsCapped() throws Exception {
        queueMailForNewStudent("Uno");
        queueMailForNewStudent("Dos");

        mockMvc.perform(get(PATH + "?page=0&size=1").header("Authorization", "Bearer " + tokenWithRole("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1));
        mockMvc.perform(get(PATH + "?page=-3&size=100000").header("Authorization", "Bearer " + tokenWithRole("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void theMailboxListsOnlyEmailDeliveries() throws Exception {
        queueMailForNewStudent("Solo mail");
        Person student = persistPerson("ALUMNO");
        DeliveryEnvelope envelope = new DeliveryEnvelope("simulated-inapp:" + System.nanoTime(), DeliveryCategory.COLLECTION_REMINDER, "BEFORE_DUE_REMINDER", 3,
                student.getId(), "Invoice", 12L, LocalDate.of(2027, 3, 9));
        deliveryService.enqueue(new DeliveryRequest(envelope, NotificationType.COLLECTION_REMINDER, DeliveryContent.forInApp("Solo campanita"), null, false));

        mockMvc.perform(get(PATH + "?size=100").header("Authorization", "Bearer " + tokenWithRole("DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Solo campanita"))))
                .andExpect(content().string(containsString("Solo mail")));
    }
}
