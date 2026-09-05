package com.red_escuela.backend.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.InstitutionRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caso "PDF de 6MB -> 400" del checklist de verificación de la spec: necesita un
 * servidor HTTP real (RANDOM_PORT), no MockMvc, porque MaxUploadSizeExceededException
 * solo se dispara en el parseo real de multipart del servlet container — MockMvc lo
 * bypassea (ver comentario en ProspectControllerTest). Con RANDOM_PORT la request de
 * TestRestTemplate corre en un thread distinto al del test, así que @Transactional no
 * sirve para aislar los datos (esa transacción no es visible desde el otro thread) —
 * se limpia todo a mano en @AfterEach en vez de depender de un rollback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProspectDocumentUploadLimitTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private Student student;
    private Person preceptor;
    private Institution institution;

    @AfterEach
    void clear() {
        if (student != null) {
            Long personId = student.getPerson().getId();
            Long addressId = student.getPerson().getAddress().getId();
            studentRepository.delete(student);
            personRepository.deleteById(personId);
            addressRepository.deleteById(addressId);
        }
        if (preceptor != null) {
            personRepository.delete(preceptor);
            addressRepository.deleteById(preceptor.getAddress().getId());
        }
        if (institution != null) {
            institutionRepository.delete(institution);
        }
    }

    @Test
    void addDocumentWithPdfExceededReturns400() {
        institution = new Institution();
        institution.setName("Instituto de prueba");
        institution = institutionRepository.save(institution);

        Address studentAddress = addressRepository.save(new Address());
        Person studentPerson = new Person();
        studentPerson.setName("Ana");
        studentPerson.setLastname("Gómez");
        studentPerson.setEmail("ana.limite@example.com");
        studentPerson.setAddress(studentAddress);
        studentPerson.setStatus(StatusType.ACTIVE);
        studentPerson = personRepository.save(studentPerson);

        student = new Student();
        student.setPerson(studentPerson);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.PROSPECTO);
        student = studentRepository.save(student);

        Address preceptorAddress = addressRepository.save(new Address());
        preceptor = new Person();
        preceptor.setUsername("preceptor-limite");
        preceptor.setEmail("preceptor-limite@example.com");
        preceptor.setAddress(preceptorAddress);
        preceptor.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("PRECEPTOR").orElseThrow();
        preceptor.getRoles().add(role);
        preceptor = personRepository.save(preceptor);

        String token = jwtService.generateAccessToken(preceptor);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[6 * 1024 * 1024]) {
            @Override
            public String getFilename() {
                return "dni.pdf";
            }
        });
        body.add("type", "DNI_FRENTE");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/prospects/{id}/documents", new HttpEntity<>(body, headers), String.class, student.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
