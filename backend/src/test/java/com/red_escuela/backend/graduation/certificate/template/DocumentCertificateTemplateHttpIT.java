package com.red_escuela.backend.graduation.certificate.template;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
class DocumentCertificateTemplateHttpIT extends AbstractIntegrationTest {

    private static final String[] DOCUMENT_TYPES = {"REGULAR_STUDENT_CERTIFICATE", "APPROVED_SUBJECTS_CERTIFICATE",
            "EXAM_REGISTRATION_CERTIFICATE", "EXAM_CERTIFICATE"};
    private static final String REGULAR = "/certificate-templates/documents/REGULAR_STUDENT_CERTIFICATE";
    private static final String DRAFT = """
            {"content":{"instituteName":"INSTITUTO NUEVO","title":"CONSTANCIA NUEVA","introText":"Se certifica que {holder} cursa {titleName}",
            "approvalText":"Para presentar ante: {recipient}","qrLabel":"QR","footerLegend":"Leyenda nueva",
            "signatory1Id":%SIG1%,"signatory2Id":%SIG2%}}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    private String director;
    private String administrative;
    private String student;
    private int firstSignatoryId;
    private int secondSignatoryId;

    @BeforeEach
    void createUsers() {
        director = tokenFor("DIRECTOR", 1);
        administrative = tokenFor("ADMINISTRATIVO", 2);
        student = tokenFor("ALUMNO", 3);
        firstSignatoryId = createSignatory("Ana Gómez", "Secretaria Legal", "REGULAR_STUDENT_CERTIFICATE");
        secondSignatoryId = createSignatory("Luis Pérez", "Director Académico", "REGULAR_STUDENT_CERTIFICATE");
    }

    private int createSignatory(String name, String role, String documentType) {
        try {
            String body = "{\"name\":\"" + name + "\",\"role\":\"" + role + "\",\"allowedDocumentTypes\":[\"" + documentType + "\"]}";
            return JsonPath.read(send(post("/certificate-signatories").contentType(MediaType.APPLICATION_JSON).content(body), director)
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String tokenFor(String roleName, int index) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("PlantillaDoc");
        person.setLastname(roleName);
        person.setUsername("doctemplate." + roleName.toLowerCase());
        person.setDni(String.valueOf(92_000_000 + index));
        person.setEmail("doctemplate." + roleName.toLowerCase() + "@example.com");
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

    private String draftBody(Integer first, Integer second) {
        return DRAFT.replace("%SIG1%", String.valueOf(first)).replace("%SIG2%", String.valueOf(second));
    }

    private String withBasedOn(String body, int versionNumber) {
        String trimmed = body.trim();
        return trimmed.substring(0, trimmed.length() - 1) + ",\"basedOnVersionNumber\":" + versionNumber + "}";
    }

    private ResultActions saveDraft(String token, String body) throws Exception {
        return send(put(REGULAR + "/draft").contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void getReturnsSeededPublishedVersionOneForEachDocumentType() throws Exception {
        for (String type : DOCUMENT_TYPES) {
            send(get("/certificate-templates/documents/" + type), administrative)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentType").value(type))
                    .andExpect(jsonPath("$.published.versionNumber").value(1))
                    .andExpect(jsonPath("$.published.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.draft").doesNotExist())
                    .andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
        }
    }

    @Test
    void getReturnsAllowedMarkersOfEachDocumentType() throws Exception {
        send(get(REGULAR), administrative)
                .andExpect(jsonPath("$.allowedMarkers", containsInAnyOrder("holder", "documentType", "documentNumber", "issuedOn",
                        "registryNumber", "titleName", "recipient")));
        send(get("/certificate-templates/documents/APPROVED_SUBJECTS_CERTIFICATE"), administrative)
                .andExpect(jsonPath("$.allowedMarkers", containsInAnyOrder("holder", "documentType", "documentNumber", "issuedOn",
                        "registryNumber", "titleName", "average", "completionPercent")));
        for (String type : new String[] {"EXAM_REGISTRATION_CERTIFICATE", "EXAM_CERTIFICATE"}) {
            send(get("/certificate-templates/documents/" + type), administrative)
                    .andExpect(jsonPath("$.allowedMarkers", containsInAnyOrder("holder", "documentType", "documentNumber", "issuedOn",
                            "registryNumber", "subjectName", "examDate", "recipient")));
        }
    }

    @Test
    void draftKeepsRequestedPublishedVersionAsBase() throws Exception {
        saveDraft(director, withBasedOn(draftBody(firstSignatoryId, secondSignatoryId), 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basedOnVersionNumber").value(1));
    }

    @Test
    void draftBasedOnUnknownVersionIsRejected() throws Exception {
        saveDraft(director, withBasedOn(draftBody(firstSignatoryId, secondSignatoryId), 99))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentCannotReadDocumentTemplates() throws Exception {
        send(get(REGULAR), student).andExpect(status().isForbidden());
    }

    @Test
    void directorSavesDraftWithTwoSignatoriesWithoutTouchingPublishedVersion() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.basedOnVersionNumber").value(1))
                .andExpect(jsonPath("$.content.signatory1Id").value(firstSignatoryId))
                .andExpect(jsonPath("$.content.firstSigner.name").value("Ana Gómez"))
                .andExpect(jsonPath("$.content.secondSigner.role").value("Director Académico"));

        send(get(REGULAR), administrative)
                .andExpect(jsonPath("$.published.versionNumber").value(1))
                .andExpect(jsonPath("$.published.content.title").value("Constancia de alumno regular"))
                .andExpect(jsonPath("$.draft.content.title").value("CONSTANCIA NUEVA"))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));
    }

    @Test
    void administrativeCannotWriteDocumentTemplates() throws Exception {
        saveDraft(administrative, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isForbidden());
        send(delete(REGULAR + "/draft"), administrative).andExpect(status().isForbidden());
        mockMvc.perform(multipart(REGULAR + "/images").file(new MockMultipartFile("file", "logo.png", "image/png", png()))
                .param("purpose", "LOGO").header("Authorization", "Bearer " + administrative)).andExpect(status().isForbidden());
    }

    @Test
    void markerOfAnotherDocumentTypeIsRejected() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId).replace("{recipient}", "{average}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_UNKNOWN_MARKER"));
    }

    @Test
    void signatoryNotAllowedForTheDocumentTypeIsRejected() throws Exception {
        int examOnly = createSignatory("Eva Soto", "Docente", "EXAM_CERTIFICATE");
        saveDraft(director, draftBody(firstSignatoryId, examOnly)).andExpect(status().isConflict());
    }

    @Test
    void sameSignatoryTwiceIsRejected() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, firstSignatoryId)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SIGNATORY_DUPLICATED_IN_TEMPLATE"));
    }

    @Test
    void discardRemovesDraftAndSecondDiscardFails() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(delete(REGULAR + "/draft"), director).andExpect(status().isNoContent());
        send(get(REGULAR), director).andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
        send(delete(REGULAR + "/draft"), director).andExpect(status().isConflict());
    }

    @Test
    void draftsOfDifferentDocumentTypesAreIndependent() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(get("/certificate-templates/documents/EXAM_CERTIFICATE"), director)
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
        send(get("/certificate-templates/PROGRAM"), director)
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
    }

    @Test
    void directorUploadsLogoAndAdministrativeReadsItBack() throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart(REGULAR + "/images").file(new MockMultipartFile("file", "logo.png", "image/png", png()))
                .param("purpose", "LOGO").header("Authorization", "Bearer " + director)).andExpect(status().isCreated()).andReturn();
        int imageId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id");

        MvcResult image = send(get(REGULAR + "/images/" + imageId), administrative).andExpect(status().isOk()).andReturn();
        assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(png());
        send(get("/certificate-templates/documents/EXAM_CERTIFICATE/images/" + imageId), administrative)
                .andExpect(status().isNotFound());
    }
}
