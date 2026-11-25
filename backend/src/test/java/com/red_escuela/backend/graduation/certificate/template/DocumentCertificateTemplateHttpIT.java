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

    private void uploadSignature(int signatoryId) throws Exception {
        mockMvc.perform(multipart("/certificate-signatories/" + signatoryId + "/signature")
                        .file(new MockMultipartFile("file", "firma.png", "image/png", png()))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }).header("Authorization", "Bearer " + director))
                .andExpect(status().isOk());
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
    void existingDraftWithUnknownBaseVersionIsRejected() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        saveDraft(director, withBasedOn(draftBody(firstSignatoryId, secondSignatoryId), 99)).andExpect(status().isNotFound());
    }

    @Test
    void draftIsBasedOnRequestedPublishedVersionOrOtherwiseOnTheLatest() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        entityManager.createNativeQuery("update certificate_template_version set status = 'PUBLISHED', version_number = 2, "
                + "published_at = now(), published_by_name = 'Test' where status = 'DRAFT' and template_id = "
                + "(select id from certificate_template where document_type = 'REGULAR_STUDENT_CERTIFICATE')").executeUpdate();
        entityManager.flush();
        entityManager.clear();

        saveDraft(director, withBasedOn(draftBody(firstSignatoryId, secondSignatoryId), 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.basedOnVersionNumber").value(1));
        send(delete(REGULAR + "/draft"), director).andExpect(status().isNoContent());
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.basedOnVersionNumber").value(2));
    }

    @Test
    void signatoryWithSignatureCopiesItToTheTemplateAndReusesItBySha256() throws Exception {
        uploadSignature(firstSignatoryId);
        String first = saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int imageId = JsonPath.read(first, "$.content.firstSigner.signatureImageId");
        assertThat(JsonPath.<Object>read(first, "$.content.secondSigner.signatureImageId")).isNull();
        send(get(REGULAR + "/images/" + imageId), administrative).andExpect(status().isOk());
        send(get("/certificate-templates/documents/EXAM_CERTIFICATE/images/" + imageId), administrative)
                .andExpect(status().isNotFound());
        String second = saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(second, "$.content.firstSigner.signatureImageId")).isEqualTo(imageId);
    }

    @Test
    void signatoryWithoutSignatureIsSavedWithoutSignatureImage() throws Exception {
        String body = saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Object>read(body, "$.content.firstSigner.signatureImageId")).isNull();
        assertThat(JsonPath.<Object>read(body, "$.content.firstSigner.name")).isEqualTo("Ana Gómez");
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
                .andExpect(jsonPath("$.published.content.title").value("CONSTANCIA DE ALUMNO REGULAR"))
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

    private static final String PUBLISHED_PREVIEW = "{\"source\":\"PUBLISHED\"}";
    private static final String DRAFT_PREVIEW = "{\"source\":\"DRAFT\"}";

    @Test
    void directorPublishesDraftWithSignatureAsNextVersionAndDraftDisappears() throws Exception {
        uploadSignature(firstSignatoryId);
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(post(REGULAR + "/publish"), director)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content.title").value("CONSTANCIA NUEVA"))
                .andExpect(jsonPath("$.content.firstSigner.name").value("Ana Gómez"));
        send(get(REGULAR), administrative)
                .andExpect(jsonPath("$.published.versionNumber").value(2))
                .andExpect(jsonPath("$.draft").doesNotExist())
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
        send(get("/certificate-templates/documents/EXAM_CERTIFICATE"), administrative)
                .andExpect(jsonPath("$.published.versionNumber").value(1));
    }

    @Test
    void publishWithoutAnySignatureIsRejected() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(post(REGULAR + "/publish"), director)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_SIGNATURE_MISSING"));
        send(get(REGULAR), administrative)
                .andExpect(jsonPath("$.published.versionNumber").value(1))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));
    }

    @Test
    void publishWithOnlyOneSignatoryWithSignatureIsAccepted() throws Exception {
        uploadSignature(secondSignatoryId);
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(post(REGULAR + "/publish"), director).andExpect(status().isOk()).andExpect(jsonPath("$.versionNumber").value(2));
    }

    @Test
    void publishPicksUpSignatureUploadedAfterTheDraftWasSaved() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        uploadSignature(firstSignatoryId);
        String published = send(post(REGULAR + "/publish"), director).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Object>read(published, "$.content.firstSigner.signatureImageId")).isNotNull();
    }

    @Test
    void publishWithoutDraftIsConflict() throws Exception {
        send(post(REGULAR + "/publish"), director)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOTHING_TO_PUBLISH"));
    }

    @Test
    void publishingTwiceTheSameDraftIsConflictTheSecondTime() throws Exception {
        uploadSignature(firstSignatoryId);
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(post(REGULAR + "/publish"), director).andExpect(status().isOk());
        send(post(REGULAR + "/publish"), director)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOTHING_TO_PUBLISH"));
    }

    @Test
    void administrativeCannotPublish() throws Exception {
        uploadSignature(firstSignatoryId);
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(post(REGULAR + "/publish"), administrative).andExpect(status().isForbidden());
        send(post(REGULAR + "/publish"), student).andExpect(status().isForbidden());
        send(get(REGULAR), administrative).andExpect(jsonPath("$.published.versionNumber").value(1));
    }

    @Test
    void versionsListPublishedVersionsNewestFirstAndAdministrativeReadsThem() throws Exception {
        uploadSignature(firstSignatoryId);
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(post(REGULAR + "/publish"), director).andExpect(status().isOk());
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId).replace("CONSTANCIA NUEVA", "CONSTANCIA OTRA"))
                .andExpect(status().isOk());
        send(get(REGULAR + "/versions"), administrative)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1));
        send(get(REGULAR + "/versions/1"), administrative)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.content.title").value("CONSTANCIA DE ALUMNO REGULAR"));
        send(get(REGULAR + "/versions/2"), administrative)
                .andExpect(jsonPath("$.content.title").value("CONSTANCIA NUEVA"));
        send(get(REGULAR + "/versions"), student).andExpect(status().isForbidden());
    }

    @Test
    void versionsAreIsolatedByDocumentType() throws Exception {
        uploadSignature(firstSignatoryId);
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(post(REGULAR + "/publish"), director).andExpect(status().isOk());
        send(get("/certificate-templates/documents/EXAM_CERTIFICATE/versions"), administrative)
                .andExpect(jsonPath("$.length()").value(1));
        send(get("/certificate-templates/documents/EXAM_CERTIFICATE/versions/2"), administrative)
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownVersionIsNotFound() throws Exception {
        send(get(REGULAR + "/versions/99"), administrative)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_VERSION_NOT_FOUND"));
    }

    @Test
    void draftIsNotReadableAsAPublishedVersion() throws Exception {
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        send(get(REGULAR + "/versions"), administrative).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void previewOfPublishedVersionReturnsPdfForAdministrativeAndDirector() throws Exception {
        for (String token : new String[] {administrative, director}) {
            MvcResult result = send(post(REGULAR + "/preview").contentType(MediaType.APPLICATION_JSON).content(PUBLISHED_PREVIEW), token)
                    .andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getContentType()).isEqualTo("application/pdf");
            assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
        }
    }

    @Test
    void previewOfDraftIsDirectorOnlyAndRequiresADraft() throws Exception {
        send(post(REGULAR + "/preview").contentType(MediaType.APPLICATION_JSON).content(DRAFT_PREVIEW), director)
                .andExpect(status().isNotFound());
        saveDraft(director, draftBody(firstSignatoryId, secondSignatoryId)).andExpect(status().isOk());
        MvcResult result = send(post(REGULAR + "/preview").contentType(MediaType.APPLICATION_JSON).content(DRAFT_PREVIEW), director)
                .andExpect(status().isOk()).andReturn();
        assertThat(new String(result.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
        send(post(REGULAR + "/preview").contentType(MediaType.APPLICATION_JSON).content(DRAFT_PREVIEW), administrative)
                .andExpect(status().isForbidden());
    }

    @Test
    void previewWorksForEveryDocumentType() throws Exception {
        for (String type : DOCUMENT_TYPES) {
            send(post("/certificate-templates/documents/" + type + "/preview").contentType(MediaType.APPLICATION_JSON)
                    .content(PUBLISHED_PREVIEW), administrative).andExpect(status().isOk());
        }
    }
}
