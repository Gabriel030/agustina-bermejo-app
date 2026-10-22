package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

abstract class AbstractBankTransferReceiptHttpIT extends AbstractBankTransferReceiptIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    JwtService jwtService;
    @Autowired
    EntityManager entityManager;

    final AtomicInteger operationCounter = new AtomicInteger();
    final Map<Long, Long> filesAtStart = new HashMap<>();

    @Override
    Debtor debtor() {
        Debtor debtor = super.debtor();
        filesAtStart.put(debtor.studentId(), countFilesOf(debtor.studentId()));
        return debtor;
    }

    String tokenOf(Person person, String role) {
        return inTransaction(() -> {
            Person managed = entityManager.find(Person.class, person.getId());
            managed.getRoles().add(roleRepository.findByName(role).orElseThrow());
            return jwtService.generateAccessToken(managed);
        });
    }

    Person personOf(Debtor debtor) {
        return inTransaction(() -> studentRepository.findById(debtor.studentId()).orElseThrow().getPerson());
    }

    String studentTokenOf(Debtor debtor) {
        return tokenOf(personOf(debtor), "ALUMNO");
    }

    String staffToken(String role) {
        return tokenOf(newStaffPerson(), role);
    }

    String nextOperationNumber() {
        return "HTTP-OP-" + operationCounter.incrementAndGet() + "-" + System.nanoTime();
    }

    static byte[] pdfBytes(String marker) {
        return ("%PDF-1.4 " + marker).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] pngBytes(String marker) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] body = marker.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[signature.length + body.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        System.arraycopy(body, 0, bytes, signature.length, body.length);
        return bytes;
    }

    static MockMultipartFile pdfFile(String marker) {
        return new MockMultipartFile("file", "comprobante.pdf", "application/pdf", pdfBytes(marker));
    }

    Submission ownSubmission(Debtor debtor, List<Long> invoiceIds, String amount) {
        return new Submission(null, studentTokenOf(debtor), invoiceIds, amount);
    }

    Submission staffSubmission(Debtor debtor, String token, List<Long> invoiceIds, String amount) {
        return new Submission(debtor.studentId(), token, invoiceIds, amount);
    }

    final class Submission {
        Long onBehalfOfStudentId;
        String token;
        MockMultipartFile file = pdfFile("contenido-" + System.nanoTime());
        List<Long> invoiceIds;
        String declaredAmount;
        String currency = "ARS";
        String transferDate = "2027-03-09";
        String operationNumber = nextOperationNumber();
        String originAccount = "0170099220000067797370";
        String holderName = "Ana Pérez";
        String bankName = "Banco Nación";
        String idempotencyKey = nextKey();

        Submission(Long onBehalfOfStudentId, String token, List<Long> invoiceIds, String declaredAmount) {
            this.onBehalfOfStudentId = onBehalfOfStudentId;
            this.token = token;
            this.invoiceIds = invoiceIds;
            this.declaredAmount = declaredAmount;
        }

        Submission withFile(MockMultipartFile newFile) {
            this.file = newFile;
            return this;
        }

        Submission withKey(String key) {
            this.idempotencyKey = key;
            return this;
        }

        Submission withOperationNumber(String number) {
            this.operationNumber = number;
            return this;
        }

        Submission withCurrency(String newCurrency) {
            this.currency = newCurrency;
            return this;
        }

        Submission withTransferDate(String date) {
            this.transferDate = date;
            return this;
        }

        Submission without(String field) {
            switch (field) {
                case "file" -> file = null;
                case "invoiceIds" -> invoiceIds = null;
                case "declaredAmount" -> declaredAmount = null;
                case "currency" -> currency = null;
                case "transferDate" -> transferDate = null;
                case "operationNumber" -> operationNumber = null;
                case "originAccount" -> originAccount = null;
                case "holderName" -> holderName = null;
                case "idempotencyKey" -> idempotencyKey = null;
                default -> throw new IllegalArgumentException(field);
            }
            return this;
        }

        MockMultipartHttpServletRequestBuilder request() {
            MockMultipartHttpServletRequestBuilder builder = onBehalfOfStudentId == null ? multipart("/me/bank-transfer-receipts")
                    : multipart("/students/{studentId}/bank-transfer-receipts", onBehalfOfStudentId);
            if (file != null) {
                builder.file(file);
            }
            if (invoiceIds != null) {
                builder.param("invoiceIds", invoiceIds.stream().map(String::valueOf).toArray(String[]::new));
            }
            param(builder, "declaredAmount", declaredAmount);
            param(builder, "currency", currency);
            param(builder, "transferDate", transferDate);
            param(builder, "operationNumber", operationNumber);
            param(builder, "originAccount", originAccount);
            param(builder, "holderName", holderName);
            param(builder, "bankName", bankName);
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            return builder;
        }

        private void param(MockMultipartHttpServletRequestBuilder builder, String name, String value) {
            if (value != null) {
                builder.param(name, value);
            }
        }
    }

    JsonNode bodyOf(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    long filesStoredFor(Long studentId) {
        return countFilesOf(studentId) - filesAtStart.getOrDefault(studentId, 0L);
    }

    private long countFilesOf(Long studentId) {
        Path directory = Path.of(uploadsDirectory).resolve("students").resolve(studentId.toString());
        if (!Files.exists(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.count();
        } catch (IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    long paymentsOf(Long accountId) {
        return jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, accountId);
    }
}
