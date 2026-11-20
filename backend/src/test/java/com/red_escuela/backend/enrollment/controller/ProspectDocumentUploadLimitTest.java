package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ProspectDocumentUploadLimitTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

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
    void cleanUp() {
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
    void addingDocumentWithOversizedPdfReturns400() throws Exception {
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
        preceptor.setDni(Long.toString(Math.abs((long) ("preceptor-limite").hashCode())));
        preceptor.setName("Persona");
        preceptor.setLastname("Prueba");
        preceptor.setEmail("preceptor-limite@example.com");
        preceptor.setAddress(preceptorAddress);
        preceptor.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("PRECEPTOR").orElseThrow();
        preceptor.getRoles().add(role);
        preceptor = personRepository.save(preceptor);

        String token = jwtService.generateAccessToken(preceptor);

        MockMultipartFile file = new MockMultipartFile("file", "dni.pdf", "application/pdf", new byte[6 * 1024 * 1024]);

        mockMvc.perform(multipart("/prospects/{id}/documents", student.getId())
                        .file(file)
                        .param("type", "DNI_FRENTE")
                        .with(request -> {
                            request.setContent(new byte[6 * 1024 * 1024 + 1024]);
                            return request;
                        })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MAX_UPLOAD_SIZE_EXCEEDED"));
    }
}
