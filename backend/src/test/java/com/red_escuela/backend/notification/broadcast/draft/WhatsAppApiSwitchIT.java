package com.red_escuela.backend.notification.broadcast.draft;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.platform.specialist.WhatsAppProviderConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class WhatsAppApiSwitchIT extends AbstractIntegrationTest {

    private static final String URL = "/institute-settings/notifications/whatsapp";
    private static final String TURN_ON = "{\"enabled\":true,\"reason\":\"Número verificado con Meta\"}";
    private static final String WHATSAPP_DRAFT = "{\"name\":\"Aviso\",\"channel\":\"WHATSAPP_API\",\"category\":\"INSTITUTIONAL\",\"bodyText\":\"Texto\","
            + "\"segmentSpec\":{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"DOCENTE\"}]}}";

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
    private InstituteSettingsSnapshotHolder snapshotHolder;

    @Autowired
    private WhatsAppProviderConfiguration providerConfiguration;

    private String directorToken;
    private String administrativeToken;
    private String studentToken;

    @BeforeEach
    void prepare() {
        resetSetting();
        directorToken = tokenOf("DIRECTOR");
        administrativeToken = tokenOf("ADMINISTRATIVO");
        studentToken = tokenOf("ALUMNO");
    }

    @AfterEach
    void restore() {
        resetSetting();
        jdbc.update("delete from broadcast where created_by_person_id in (select id from person where username like 'wa-%')");
        jdbc.update("delete from person_role where person_id in (select id from person where username like 'wa-%')");
        jdbc.update("delete from person where username like 'wa-%'");
    }

    @Test
    void whatsappIsOffByDefault() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.providerConfigured").value(false))
                .andExpect(jsonPath("$.dailyLimit").value(250))
                .andExpect(jsonPath("$.pendingRequirements[0]").value("Proveedor de WhatsApp configurado"))
                .andExpect(jsonPath("$.pendingRequirements.length()").value(3));
    }

    @Test
    void enablingWithoutProviderFails() throws Exception {
        mockMvc.perform(put(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content(TURN_ON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WHATSAPP_PROVIDER_NOT_CONFIGURED"));

        assertThat(jdbc.queryForObject("select enabled from institute_setting where code = 'WHATSAPP_API'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from institute_setting_change_log where code = 'WHATSAPP_API'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'INSTITUTE_SETTING'", Integer.class)).isZero();
    }

    @Test
    void disablingWorksWithoutProvider() throws Exception {
        jdbc.update("update institute_setting set enabled = true where code = 'WHATSAPP_API'");
        snapshotHolder.refresh();

        mockMvc.perform(put(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'WHATSAPP_API_DISABLED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void onlyDirectorCanToggle() throws Exception {
        configureProvider();
        for (String token : new String[]{administrativeToken, studentToken}) {
            mockMvc.perform(get(URL).header("Authorization", token)).andExpect(status().isForbidden());
            mockMvc.perform(put(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(TURN_ON))
                    .andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select enabled from institute_setting where code = 'WHATSAPP_API'", Boolean.class)).isFalse();
    }

    @Test
    void disabledChannelIsNotInCatalog() throws Exception {
        mockMvc.perform(get("/broadcasts/channels").header("Authorization", administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.channel=='EMAIL')].enabled").value(true))
                .andExpect(jsonPath("$[?(@.channel=='WHATSAPP_API')].enabled").value(false));

        configureProvider();
        mockMvc.perform(put(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content(TURN_ON))
                .andExpect(status().isOk());

        mockMvc.perform(get("/broadcasts/channels").header("Authorization", administrativeToken))
                .andExpect(jsonPath("$[?(@.channel=='WHATSAPP_API')].enabled").value(true));
    }

    @Test
    void creatingWhatsAppBroadcastWhileOffIsRejected() throws Exception {
        mockMvc.perform(post("/broadcasts").header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content(WHATSAPP_DRAFT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHANNEL_NOT_ENABLED"));

        configureProvider();
        mockMvc.perform(put(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content(TURN_ON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/broadcasts").header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content(WHATSAPP_DRAFT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        assertThat(jdbc.queryForList("select channel from broadcast", String.class)).containsExactly("WHATSAPP_API");
    }

    @Test
    void toggleIsAudited() throws Exception {
        configureProvider();
        mockMvc.perform(put(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content(TURN_ON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(put(URL).header("Authorization", directorToken).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        assertThat(jdbc.queryForList("select action from audit_log where entity_type = 'INSTITUTE_SETTING' order by id", String.class))
                .containsExactly("WHATSAPP_API_ENABLED", "WHATSAPP_API_DISABLED");
        assertThat(jdbc.queryForObject("select reason from audit_log where action = 'WHATSAPP_API_ENABLED'", String.class))
                .isEqualTo("Número verificado con Meta");
        assertThat(jdbc.queryForMap("select before_value, after_value, entity_id from audit_log where action = 'WHATSAPP_API_ENABLED'"))
                .containsEntry("before_value", "false").containsEntry("after_value", "true").containsEntry("entity_id", 1L);
        assertThat(jdbc.queryForMap("select before_value, after_value, entity_id from audit_log where action = 'WHATSAPP_API_DISABLED'"))
                .containsEntry("before_value", "true").containsEntry("after_value", "false").containsEntry("entity_id", 1L);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_type = 'INSTITUTE_SETTING' and actor_person_id is not null and occurred_at is not null", Integer.class))
                .isEqualTo(2);
    }

    private void configureProvider() {
        doReturn(true).when(providerConfiguration).isConfigured();
    }

    private void resetSetting() {
        jdbc.update("delete from audit_log where entity_type = 'INSTITUTE_SETTING'");
        jdbc.update("delete from institute_setting_change_log where code = 'WHATSAPP_API'");
        jdbc.update("update institute_setting set enabled = false, version = 0, updated_by_id = null where code = 'WHATSAPP_API'");
        snapshotHolder.refresh();
    }

    private String tokenOf(String role) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Person person = AudienceFreezeFixtures.person(entityManager, roleRepository, "wa-" + role.toLowerCase() + "-" + System.nanoTime(), true, role);
            return "Bearer " + jwtService.generateAccessToken(person);
        });
    }
}
