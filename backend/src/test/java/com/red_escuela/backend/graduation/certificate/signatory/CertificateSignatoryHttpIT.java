package com.red_escuela.backend.graduation.certificate.signatory;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CertificateSignatoryHttpIT extends AbstractIntegrationTest {

    private static final String BASE = "/certificate-signatories";
    private static final String BODY = "{\"name\":\"Marta Ruiz\",\"role\":\"Rectora\",\"allowedDocumentTypes\":[\"GRADUATION_APPROVAL\",\"EXAM_CERTIFICATE\"]}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private String director;
    private String administrative;
    private String student;

    @BeforeEach
    void createUsers() {
        director = tokenFor("DIRECTOR", 1);
        administrative = tokenFor("ADMINISTRATIVO", 2);
        student = tokenFor("ALUMNO", 3);
    }

    private String tokenFor(String roleName, int index) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Firmante");
        person.setLastname(roleName);
        person.setUsername("signatory." + roleName.toLowerCase());
        person.setDni(String.valueOf(92_000_000 + index));
        person.setEmail("signatory." + roleName.toLowerCase() + "@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        return send(request.contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private int create(String body) throws Exception {
        return JsonPath.read(json(post(BASE), director, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions uploadSignature(int id, String token, byte[] content, String contentType) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PUT, BASE + "/" + id + "/signature")
                .file(new MockMultipartFile("file", "firma.png", contentType, content)).header("Authorization", "Bearer " + token));
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void directorCreatesEditsAndDeactivatesASignatory() throws Exception {
        int id = create(BODY);

        json(put(BASE + "/" + id), director, "{\"name\":\"Marta R.\",\"role\":\"Directora\",\"allowedDocumentTypes\":[\"GRADUATION_APPROVAL\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Marta R."))
                .andExpect(jsonPath("$.allowedDocumentTypes.length()").value(1));
        send(post(BASE + "/" + id + "/deactivate"), director).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        send(post(BASE + "/" + id + "/activate"), director).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        send(get(BASE + "/" + id), administrative).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("Directora"));
    }

    @Test
    void listFiltersByActiveQueryAndDocumentTypeAndIncludesTheSeededSignatories() throws Exception {
        int id = create(BODY);
        send(post(BASE + "/" + id + "/deactivate"), director).andExpect(status().isOk());

        send(get(BASE).param("active", "false").param("q", "marta").param("documentType", "EXAM_CERTIFICATE"), administrative)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        send(get(BASE).param("active", "true").param("documentType", "GRADUATION_APPROVAL"), director)
                .andExpect(jsonPath("$.content[?(@.role=='Secretaria Legal')]").isNotEmpty());
    }

    @Test
    void unauthorizedRolesCannotWriteAndStudentsCannotRead() throws Exception {
        int id = create(BODY);

        json(post(BASE), administrative, BODY).andExpect(status().isForbidden());
        json(put(BASE + "/" + id), administrative, BODY).andExpect(status().isForbidden());
        send(post(BASE + "/" + id + "/deactivate"), administrative).andExpect(status().isForbidden());
        send(post(BASE + "/" + id + "/activate"), administrative).andExpect(status().isForbidden());
        uploadSignature(id, administrative, png(), "image/png").andExpect(status().isForbidden());
        send(delete(BASE + "/" + id + "/signature"), administrative).andExpect(status().isForbidden());
        send(get(BASE), student).andExpect(status().isForbidden());
        send(get(BASE + "/" + id + "/signature"), student).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBodyAndMissingSignatoryAreRejected() throws Exception {
        json(post(BASE), director, "{\"name\":\" \",\"role\":\"Rectora\",\"allowedDocumentTypes\":[]}").andExpect(status().isBadRequest());
        json(post(BASE), director, "{\"name\":\"Ana\",\"role\":\"   \",\"allowedDocumentTypes\":[]}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SIGNATORY_ROLE_INVALID"));
        json(post(BASE), director, "{\"name\":\"Ana\",\"role\":\"" + "x".repeat(81) + "\",\"allowedDocumentTypes\":[]}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SIGNATORY_ROLE_INVALID"));
        json(post(BASE), director, "{\"name\":\"Ana\",\"role\":\"Cargo\",\"allowedDocumentTypes\":[]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.allowedDocumentTypes.length()").value(0));
        send(get(BASE + "/987654321"), director).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SIGNATORY_NOT_FOUND"));
    }

    @Test
    void signatureIsStoredServedAndDeleted() throws Exception {
        int id = create(BODY);

        uploadSignature(id, director, png(), "image/png")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSignature").value(true))
                .andExpect(jsonPath("$.signatureImageId").isNumber());
        byte[] served = send(get(BASE + "/" + id + "/signature"), administrative).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(png());
        uploadSignature(id, director, png(), "image/png").andExpect(status().isOk());

        send(delete(BASE + "/" + id + "/signature"), director).andExpect(status().isOk()).andExpect(jsonPath("$.hasSignature").value(false));
        send(get(BASE + "/" + id + "/signature"), director).andExpect(status().isNotFound());
    }

    @Test
    void nonPngOrOversizedSignatureIsRejected() throws Exception {
        int id = create(BODY);
        BufferedImage noise = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(7);
        for (int x = 0; x < 1000; x++) {
            for (int y = 0; y < 1000; y++) {
                noise.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream big = new ByteArrayOutputStream();
        ImageIO.write(noise, "png", big);
        assertThat(big.size()).isBetween(2 * 1024 * 1024 + 1, 5 * 1024 * 1024);

        uploadSignature(id, director, "<html></html>".getBytes(), "image/png")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SIGNATORY_SIGNATURE_INVALID"));
        uploadSignature(id, director, big.toByteArray(), "image/png")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SIGNATORY_SIGNATURE_INVALID"));
        send(get(BASE + "/" + id), director).andExpect(jsonPath("$.hasSignature").value(false));
    }

    @Test
    void signatureWithoutImageIsNotFound() throws Exception {
        int id = create(BODY);

        send(get(BASE + "/" + id + "/signature"), director)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SIGNATORY_NOT_FOUND"));
    }

    @Test
    void usedInTemplatesListsTheDraftThatSelectsTheSignatory() throws Exception {
        int first = create(BODY);
        int second = create("{\"name\":\"Luis\",\"role\":\"Director\",\"allowedDocumentTypes\":[\"GRADUATION_APPROVAL\"]}");
        json(put("/certificate-templates/PROGRAM/draft"), director,
                "{\"content\":{\"title\":\"T\",\"signatory1Id\":" + first + ",\"signatory2Id\":" + second + "}}").andExpect(status().isOk());

        send(get(BASE + "/" + first), director)
                .andExpect(jsonPath("$.usedInTemplates.length()").value(1))
                .andExpect(jsonPath("$.usedInTemplates[0].documentType").value("GRADUATION_APPROVAL"))
                .andExpect(jsonPath("$.usedInTemplates[0].status").value("DRAFT"));
    }

    @Test
    void auditRecordsChangesWithoutTheSignatureImage() throws Exception {
        int id = create(BODY);
        json(put(BASE + "/" + id), director, "{\"name\":\"Marta R.\",\"role\":\"Rectora\",\"allowedDocumentTypes\":[\"EXAM_CERTIFICATE\"]}").andExpect(status().isOk());
        uploadSignature(id, director, png(), "image/png").andExpect(status().isOk());
        send(delete(BASE + "/" + id + "/signature"), director).andExpect(status().isOk());
        send(post(BASE + "/" + id + "/deactivate"), director).andExpect(status().isOk());
        send(post(BASE + "/" + id + "/activate"), director).andExpect(status().isOk());
        entityManager.flush();

        List<AuditLogEntry> entries = auditLogRepository.findAll().stream().filter(entry -> entry.getEntityId().equals((long) id)).toList();

        assertThat(entries).extracting(AuditLogEntry::getAction).contains(
                AuditAction.CERTIFICATE_SIGNATORY_CREATED, AuditAction.CERTIFICATE_SIGNATORY_UPDATED,
                AuditAction.CERTIFICATE_SIGNATORY_PERMS_CHANGED, AuditAction.CERTIFICATE_SIGNATORY_SIGNATURE_UPLOADED,
                AuditAction.CERTIFICATE_SIGNATORY_SIGNATURE_DELETED, AuditAction.CERTIFICATE_SIGNATORY_DEACTIVATED,
                AuditAction.CERTIFICATE_SIGNATORY_ACTIVATED);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(String.valueOf(entry.getBeforeValue()) + entry.getAfterValue()).doesNotContain("PNG").doesNotContain("�");
        });
        assertThat(entries).filteredOn(entry -> entry.getAction() == AuditAction.CERTIFICATE_SIGNATORY_SIGNATURE_UPLOADED)
                .allSatisfy(entry -> {
                    assertThat(entry.getBeforeValue()).isNull();
                    assertThat(entry.getAfterValue()).isNull();
                });
    }
}
