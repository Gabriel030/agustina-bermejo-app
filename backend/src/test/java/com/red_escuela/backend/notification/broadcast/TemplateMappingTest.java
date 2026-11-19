package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.template.entity.MessageTemplate;
import com.red_escuela.backend.notification.broadcast.template.repository.MessageTemplateRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class TemplateMappingTest extends AbstractIntegrationTest {

    private static final String URL = "/institute-settings/notifications/whatsapp/templates";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MessageTemplateRepository messageTemplateRepository;

    private String directorToken;
    private String administrativeToken;
    private Long messageTemplateId;
    private Long creatorId;

    @BeforeEach
    void prepare() {
        jdbc.update("delete from whatsapp_template_mapping");
        directorToken = tokenOf("DIRECTOR");
        administrativeToken = tokenOf("ADMINISTRATIVO");
        Instant now = Instant.now();
        creatorId = jdbc.queryForObject("select id from person where username like ? order by id desc limit 1", Long.class, "tm-director-%");
        messageTemplateId = messageTemplateRepository.save(MessageTemplate.builder().name("tpl-" + System.nanoTime())
                .category(BroadcastCategory.INSTITUTIONAL).channel(ChannelType.WHATSAPP_API).bodyText("Hola {{nombre}}").active(true)
                .createdByPersonId(creatorId).createdAt(now).updatedAt(now).build()).getId();
    }

    @AfterEach
    void clean() {
        jdbc.update("delete from whatsapp_template_mapping");
    }

    private String body(String name, String language, String status, String variables) {
        return "{\"messageTemplateId\":" + messageTemplateId + ",\"providerTemplateName\":\"" + name + "\",\"languageCode\":\"" + language
                + "\",\"status\":\"" + status + "\",\"variableOrder\":" + variables + "}";
    }

    @Test
    void directorCreatesListsAndUpdatesAMapping() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body("aviso_general", "es_AR", "PENDING", "[\"nombre\",\"institucion\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.variableOrder[1]").value("institucion"));
        Long id = jdbc.queryForObject("select id from whatsapp_template_mapping", Long.class);

        mockMvc.perform(put(URL + "/" + id).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body("aviso_general", "es_AR", "APPROVED", "[\"nombre\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get(URL).header("Authorization", directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("APPROVED"))
                .andExpect(jsonPath("$[0].variableOrder.length()").value(1));
        assertThat(jdbc.queryForObject("select updated_by from whatsapp_template_mapping where id = ?", Long.class, id)).isNotNull();
    }

    @Test
    void onlyDirectorCanManageMappings() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", administrativeToken)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).header("Authorization", administrativeToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body("aviso_general", "es_AR", "APPROVED", "[]")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from whatsapp_template_mapping", Integer.class)).isZero();
    }

    @Test
    void unknownVariableIsRejected() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body("aviso_general", "es_AR", "APPROVED", "[\"inexistente\"]")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidProviderNameAndLanguageAreRejected() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Aviso General", "es_AR", "APPROVED", "[]")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body("aviso_general", "castellano", "APPROVED", "[]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondMappingForTheSameTemplateIsAConflict() throws Exception {
        String request = body("aviso_general", "es_AR", "APPROVED", "[]");
        mockMvc.perform(post(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WHATSAPP_TEMPLATE_MAPPING_ALREADY_EXISTS"));
    }

    @Test
    void mappingForAnUnknownMessageTemplateIsNotFound() throws Exception {
        mockMvc.perform(post(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageTemplateId\":999999999,\"providerTemplateName\":\"aviso\",\"languageCode\":\"es_AR\",\"status\":\"APPROVED\",\"variableOrder\":[]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(URL + "/999999999").header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body("aviso_general", "es_AR", "APPROVED", "[]")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WHATSAPP_TEMPLATE_MAPPING_NOT_FOUND"));
    }

    private String tokenOf(String role) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String username = "tm-" + role.toLowerCase() + "-" + System.nanoTime();
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setUsername(username);
            person.setName("Nombre");
            person.setLastname(username);
            person.setEmail(username + "@example.com");
            person.setEmailVerified(true);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            entityManager.persist(person);
            entityManager.flush();
            return "Bearer " + jwtService.generateAccessToken(person);
        });
    }
}
