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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

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
class CertificateTemplateHttpIT extends AbstractIntegrationTest {

    private static final String BASE = "/certificate-templates/PROGRAM";
    private static final String DRAFT_TEMPLATE = """
            {"content":{"instituteName":"INSTITUTO NUEVO","title":"CERTIFICADO NUEVO","introText":"Se certifica que",
            "approvalText":"ha aprobado {titleKind} {titleName}","qrLabel":"QR","footerLegend":"Leyenda nueva",
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
    private String draftBody;
    private int firstSignatoryId;
    private int secondSignatoryId;

    @BeforeEach
    void createUsers() {
        director = tokenFor("DIRECTOR", 1);
        administrative = tokenFor("ADMINISTRATIVO", 2);
        student = tokenFor("ALUMNO", 3);
        firstSignatoryId = createSignatory("Ana Gómez", "Secretaria Legal");
        secondSignatoryId = createSignatory("Luis Pérez", "Director Académico");
        draftBody = DRAFT_TEMPLATE.replace("%SIG1%", String.valueOf(firstSignatoryId)).replace("%SIG2%", String.valueOf(secondSignatoryId));
    }

    private int createSignatory(String name, String role) {
        try {
            String body = "{\"name\":\"" + name + "\",\"role\":\"" + role + "\",\"allowedDocumentTypes\":[\"GRADUATION_APPROVAL\"]}";
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
        person.setName("Plantilla");
        person.setLastname(roleName);
        person.setUsername("template." + roleName.toLowerCase());
        person.setDni(String.valueOf(91_000_000 + index));
        person.setEmail("template." + roleName.toLowerCase() + "@example.com");
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

    private ResultActions saveDraft(String token, String body) throws Exception {
        return send(put(BASE + "/draft").contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private ResultActions upload(String token, String purpose, byte[] content, String contentType) throws Exception {
        return mockMvc.perform(multipart(BASE + "/images").file(new MockMultipartFile("file", "imagen.png", contentType, content))
                .param("purpose", purpose).header("Authorization", "Bearer " + token));
    }

    private static byte[] png(int type) throws IOException {
        BufferedImage image = new BufferedImage(20, 10, type);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private int publishedVersionNumber() throws Exception {
        MvcResult result = send(get(BASE), director).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.published.versionNumber");
    }

    @Test
    void publishingCreatesNextVersionAndLeavesPreviousUntouched() throws Exception {
        saveDraft(director, draftBody).andExpect(status().isOk());

        send(post(BASE + "/publish"), director)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content.title").value("CERTIFICADO NUEVO"))
                .andExpect(jsonPath("$.publishedBy").value("DIRECTOR, Plantilla"));
        send(get(BASE + "/versions/1"), administrative)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content.title").value("CERTIFICADO DE APROBACIÓN"))
                .andExpect(jsonPath("$.content.instituteName").value("INSTITUTO AGUSTINA BERMEJO"));
        send(get(BASE + "/versions"), administrative)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1));
        send(get(BASE), director)
                .andExpect(jsonPath("$.published.versionNumber").value(2))
                .andExpect(jsonPath("$.draft").doesNotExist())
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false));
    }

    @Test
    void editingDraftDoesNotChangePublishedVersion() throws Exception {
        saveDraft(director, draftBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.basedOnVersionNumber").value(1));

        send(get(BASE), administrative)
                .andExpect(jsonPath("$.published.versionNumber").value(1))
                .andExpect(jsonPath("$.published.content.title").value("CERTIFICADO DE APROBACIÓN"))
                .andExpect(jsonPath("$.draft.content.title").value("CERTIFICADO NUEVO"))
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(true));
    }

    @Test
    void unknownMarkerIsRejectedWithUnprocessableEntity() throws Exception {
        saveDraft(director, draftBody.replace("{titleName}", "{nickname}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_UNKNOWN_MARKER"));
    }

    @Test
    void unbalancedBracesAreRejectedAsUnknownMarker() throws Exception {
        for (String broken : new String[]{"{holder", "holder}", "{{holder}", "{holder}}", "{}"}) {
            saveDraft(director, draftBody.replace("{titleName}", broken))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("TEMPLATE_UNKNOWN_MARKER"));
        }
    }

    @Test
    void validLargePngOverTwoMegabytesIsRejectedBySize() throws Exception {
        BufferedImage noise = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(7);
        for (int x = 0; x < 1000; x++) {
            for (int y = 0; y < 1000; y++) {
                noise.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(noise, "png", out);
        assertThat(out.size()).isGreaterThan(2 * 1024 * 1024);

        upload(director, "LOGO", out.toByteArray(), "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_INVALID"));
    }

    @Test
    void validPngWithAlteredSignatureIsRejected() throws Exception {
        byte[] altered = png(BufferedImage.TYPE_INT_ARGB);
        altered[1] = 'X';

        upload(director, "LOGO", altered, "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_INVALID"));
    }

    @Test
    void publishingCopiesTheSignatorySignatureIntoTheVersionSnapshot() throws Exception {
        int logoId = JsonPath.read(upload(director, "LOGO", png(BufferedImage.TYPE_INT_RGB), "image/png")
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(multipart(HttpMethod.PUT, "/certificate-signatories/" + firstSignatoryId + "/signature")
                        .file(new MockMultipartFile("file", "firma.png", "image/png", png(BufferedImage.TYPE_INT_ARGB)))
                        .header("Authorization", "Bearer " + director))
                .andExpect(status().isOk());
        saveDraft(director, draftBody.replace("\"instituteName\"", "\"logoImageId\":" + logoId + ",\"instituteName\""))
                .andExpect(status().isOk());

        MvcResult published = send(post(BASE + "/publish"), director)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.content.logoImageId").value(logoId))
                .andExpect(jsonPath("$.content.signatory1Id").value(firstSignatoryId))
                .andExpect(jsonPath("$.content.signatory2Id").value(secondSignatoryId))
                .andExpect(jsonPath("$.content.firstSigner.name").value("Ana Gómez"))
                .andExpect(jsonPath("$.content.secondSigner.signatureImageId").doesNotExist())
                .andReturn();
        int snapshotImageId = JsonPath.read(published.getResponse().getContentAsString(), "$.content.firstSigner.signatureImageId");
        send(get(BASE + "/images/" + snapshotImageId), administrative).andExpect(status().isOk());
        send(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"PUBLISHED\"}"), administrative)
                .andExpect(status().isOk());
    }

    @Test
    void editingOrDeactivatingTheSignatoryDoesNotChangeThePublishedSnapshot() throws Exception {
        saveDraft(director, draftBody).andExpect(status().isOk());
        send(post(BASE + "/publish"), director).andExpect(status().isOk());

        send(put("/certificate-signatories/" + firstSignatoryId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nombre Cambiado\",\"role\":\"Otro cargo\",\"allowedDocumentTypes\":[\"GRADUATION_APPROVAL\"]}"), director)
                .andExpect(status().isOk());
        send(post("/certificate-signatories/" + firstSignatoryId + "/deactivate"), director).andExpect(status().isOk());

        send(get(BASE + "/versions/2"), administrative)
                .andExpect(jsonPath("$.content.firstSigner.name").value("Ana Gómez"))
                .andExpect(jsonPath("$.content.firstSigner.role").value("Secretaria Legal"));
        send(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"PUBLISHED\"}"), administrative)
                .andExpect(status().isOk());
    }

    @Test
    void signatoryWithoutPermissionForTheDocumentTypeIsConflict() throws Exception {
        int other = JsonPath.read(send(post("/certificate-signatories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sin Permiso\",\"role\":\"Cargo\",\"allowedDocumentTypes\":[\"EXAM_CERTIFICATE\"]}"), director)
                .andReturn().getResponse().getContentAsString(), "$.id");

        saveDraft(director, draftBody.replace("\"signatory2Id\":" + secondSignatoryId, "\"signatory2Id\":" + other))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNATORY_NOT_ALLOWED_FOR_DOCUMENT_TYPE"));
    }

    @Test
    void inactiveRepeatedOrMissingSignatoriesAreRejected() throws Exception {
        saveDraft(director, draftBody.replace("\"signatory2Id\":" + secondSignatoryId, "\"signatory2Id\":" + firstSignatoryId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SIGNATORY_DUPLICATED_IN_TEMPLATE"));
        saveDraft(director, draftBody.replace("\"signatory2Id\":" + secondSignatoryId, "\"signatory2Id\":987654321"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SIGNATORY_NOT_FOUND"));

        saveDraft(director, draftBody).andExpect(status().isOk());
        send(post("/certificate-signatories/" + secondSignatoryId + "/deactivate"), director).andExpect(status().isOk());
        send(post(BASE + "/publish"), director)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SIGNATORY_INACTIVE"));
        saveDraft(director, draftBody).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SIGNATORY_INACTIVE"));
        assertThat(publishedVersionNumber()).isEqualTo(1);
    }

    @Test
    void everyAllowedMarkerIsAccepted() throws Exception {
        saveDraft(director, draftBody.replace("{titleKind} {titleName}",
                "{holder} {documentType} {documentNumber} {titleKind} {titleName} {hours} {graduationDate} {issuedOn} {registryNumber}"))
                .andExpect(status().isOk());
    }

    @Test
    void publishingWithoutDraftOrWithoutChangesIsConflict() throws Exception {
        send(post(BASE + "/publish"), director)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOTHING_TO_PUBLISH"));
        saveDraft(director, draftBody).andExpect(status().isOk());
        send(post(BASE + "/publish"), director).andExpect(status().isOk());
        Object publishedContent = JsonPath.read(
                send(get(BASE), director).andReturn().getResponse().getContentAsString(), "$.published.content");
        saveDraft(director, "{\"content\":" + new ObjectMapper().writeValueAsString(publishedContent) + "}")
                .andExpect(status().isOk());
        send(post(BASE + "/publish"), director)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOTHING_TO_PUBLISH"));
    }

    @Test
    void publishingWithoutTitleOrCompleteSignersIsRejected() throws Exception {
        saveDraft(director, draftBody.replace("\"title\":\"CERTIFICADO NUEVO\",", "")).andExpect(status().isOk());
        send(post(BASE + "/publish"), director)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_INCOMPLETE"));

        saveDraft(director, draftBody.replaceAll(",\\s*\"signatory2Id\":\\d+", "")).andExpect(status().isOk());
        send(post(BASE + "/publish"), director)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_INCOMPLETE"));

        saveDraft(director, draftBody.replaceAll(",\\s*\"signatory1Id\":\\d+", "")).andExpect(status().isOk());
        send(post(BASE + "/publish"), director)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_INCOMPLETE"));
        assertThat(publishedVersionNumber()).isEqualTo(1);
    }

    @Test
    void discardingDraftReturnsToLastPublishedVersion() throws Exception {
        saveDraft(director, draftBody).andExpect(status().isOk());

        send(delete(BASE + "/draft"), director).andExpect(status().isNoContent());

        send(get(BASE), director)
                .andExpect(jsonPath("$.draft").doesNotExist())
                .andExpect(jsonPath("$.hasUnpublishedChanges").value(false))
                .andExpect(jsonPath("$.published.versionNumber").value(1))
                .andExpect(jsonPath("$.published.content.title").value("CERTIFICADO DE APROBACIÓN"));
        send(delete(BASE + "/draft"), director)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOTHING_TO_DISCARD"));
    }

    @Test
    void fakePngWithPngContentTypeIsRejectedByMagicBytes() throws Exception {
        byte[] notAPng = "<html><script>alert(1)</script></html>".getBytes();

        upload(director, "LOGO", notAPng, "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_INVALID"));
    }

    @Test
    void pngSignatureWithCorruptBodyIsRejected() throws Exception {
        byte[] truncated = Arrays.copyOf(png(BufferedImage.TYPE_INT_ARGB), 20);

        upload(director, "LOGO", truncated, "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_INVALID"));
    }

    @Test
    void imageLargerThanTwoMegabytesIsRejected() throws Exception {
        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, oversized, 0, signature.length);

        upload(director, "LOGO", oversized, "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_INVALID"));
    }

    @Test
    void validLogoIsStoredServedAndUsableInTheDraft() throws Exception {
        MvcResult uploaded = upload(director, "LOGO", png(BufferedImage.TYPE_INT_RGB), "application/octet-stream")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.widthPixels").value(20))
                .andReturn();
        int imageId = JsonPath.read(uploaded.getResponse().getContentAsString(), "$.id");

        MvcResult served = send(get(BASE + "/images/" + imageId), administrative).andExpect(status().isOk()).andReturn();
        assertThat(served.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(served.getResponse().getContentAsByteArray()).isEqualTo(png(BufferedImage.TYPE_INT_RGB));
        saveDraft(director, draftBody.replace("\"instituteName\"", "\"logoImageId\":" + imageId + ",\"instituteName\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.logoImageId").value(imageId));
    }

    @Test
    void signatureMustHaveTransparency() throws Exception {
        upload(director, "SIGNATURE", png(BufferedImage.TYPE_INT_RGB), "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_INVALID"));
        upload(director, "SIGNATURE", png(BufferedImage.TYPE_INT_ARGB), "image/png").andExpect(status().isCreated());
    }

    @Test
    void draftCannotReferenceImageOfAnotherPurposeOrMissingImage() throws Exception {
        MvcResult logo = upload(director, "LOGO", png(BufferedImage.TYPE_INT_ARGB), "image/png").andReturn();
        int logoId = JsonPath.read(logo.getResponse().getContentAsString(), "$.id");
        MvcResult signature = upload(director, "SIGNATURE", png(BufferedImage.TYPE_INT_ARGB), "image/png").andReturn();
        int signatureId = JsonPath.read(signature.getResponse().getContentAsString(), "$.id");

        saveDraft(director, draftBody.replace("\"instituteName\"", "\"logoImageId\":" + signatureId + ",\"instituteName\""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_NOT_FOUND"));
        send(get(BASE + "/images/987654321"), director)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_NOT_FOUND"));
    }

    @Test
    void imageOfAnotherKindIsNotServed() throws Exception {
        MvcResult logo = upload(director, "LOGO", png(BufferedImage.TYPE_INT_ARGB), "image/png").andReturn();
        int logoId = JsonPath.read(logo.getResponse().getContentAsString(), "$.id");

        send(get("/certificate-templates/COURSE/images/" + logoId), director)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_IMAGE_NOT_FOUND"));
    }

    @Test
    void missingVersionIsNotFound() throws Exception {
        send(get(BASE + "/versions/99"), director)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_VERSION_NOT_FOUND"));
    }

    @Test
    void administrativeCanOnlyRead() throws Exception {
        saveDraft(administrative, draftBody).andExpect(status().isForbidden());
        send(delete(BASE + "/draft"), administrative).andExpect(status().isForbidden());
        send(post(BASE + "/publish"), administrative).andExpect(status().isForbidden());
        upload(administrative, "LOGO", png(BufferedImage.TYPE_INT_ARGB), "image/png").andExpect(status().isForbidden());
        send(get(BASE), administrative).andExpect(status().isOk());
        send(get(BASE + "/versions"), administrative).andExpect(status().isOk());
    }

    @Test
    void studentIsForbiddenEverywhere() throws Exception {
        send(get(BASE), student).andExpect(status().isForbidden());
        saveDraft(student, draftBody).andExpect(status().isForbidden());
        send(delete(BASE + "/draft"), student).andExpect(status().isForbidden());
        send(post(BASE + "/publish"), student).andExpect(status().isForbidden());
        upload(student, "LOGO", png(BufferedImage.TYPE_INT_ARGB), "image/png").andExpect(status().isForbidden());
        send(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"PUBLISHED\"}"), student)
                .andExpect(status().isForbidden());
    }

    @Test
    void publishedPreviewIsAvailableToAdministrativeAndDraftPreviewOnlyToDirector() throws Exception {
        String published = "{\"source\":\"PUBLISHED\"}";
        String draft = "{\"source\":\"DRAFT\"}";
        saveDraft(director, draftBody).andExpect(status().isOk());

        MvcResult pdf = send(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content(published), administrative)
                .andExpect(status().isOk())
                .andReturn();
        send(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content(draft), administrative)
                .andExpect(status().isForbidden());
        MvcResult draftPdf = send(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content(draft), director)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(textOf(pdf)).contains("VISTA PREVIA", "Secretaria Legal", "Director Académico");
        assertThat(textOf(draftPdf)).contains("VISTA PREVIA", "Ana Gómez", "Luis Pérez");
    }

    @Test
    void draftPreviewWithoutDraftIsNotFound() throws Exception {
        send(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"DRAFT\"}"), director)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_VERSION_NOT_FOUND"));
    }

    @Test
    void courseAndProgramTemplatesAreIndependent() throws Exception {
        saveDraft(director, draftBody).andExpect(status().isOk());

        send(get("/certificate-templates/COURSE"), director)
                .andExpect(jsonPath("$.draft").doesNotExist())
                .andExpect(jsonPath("$.published.content.approvalText").value("ha aprobado la Capacitación en"));
        send(get(BASE), director)
                .andExpect(jsonPath("$.published.content.approvalText").value("ha aprobado la Tecnicatura en"));
    }

    private String textOf(MvcResult result) throws IOException {
        try (PDDocument document = Loader.loadPDF(result.getResponse().getContentAsByteArray())) {
            return new PDFTextStripper().getText(document);
        }
    }
}
