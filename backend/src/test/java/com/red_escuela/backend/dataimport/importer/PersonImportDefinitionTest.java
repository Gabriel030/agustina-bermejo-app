package com.red_escuela.backend.dataimport.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.identity.api.HistoricalPersonRecorder;
import com.red_escuela.backend.identity.api.MinorAgeChecker;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonImportDefinitionTest {
    private final HistoricalPersonRecorder recorder = mock(HistoricalPersonRecorder.class);
    private final PersonImportDefinition definition = new PersonImportDefinition(recorder, new MinorAgeChecker());

    private static Map<String, String> adult() {
        Map<String, String> row = new HashMap<>();
        PersonImportDefinition.COLUMNS.forEach(column -> row.put(column, ""));
        row.put("documentType", "DNI");
        row.put("dni", "30.123.456");
        row.put("firstName", "Lucía");
        row.put("lastName", "Fernández");
        row.put("role", "STUDENT");
        row.put("birthDate", "1992-04-18");
        row.put("email", "lucia@example.com");
        row.put("registeredAt", "15/03/2022");
        return row;
    }

    private List<String> codes(Map<String, String> row) {
        return definition.validateFormat(row).stream().map(ImportRowError::code).toList();
    }

    @Test
    void validAdultStudentHasNoErrors() {
        assertThat(codes(adult())).isEmpty();
    }

    @Test
    void dniWithDotsIsNormalizedInTheNaturalKey() {
        assertThat(definition.naturalKey(adult())).isEqualTo("DNI:30123456");
    }

    @Test
    void emptyDocumentTypeDefaultsToDni() {
        Map<String, String> row = adult();
        row.put("documentType", "");

        assertThat(codes(row)).isEmpty();
        assertThat(definition.naturalKey(row)).isEqualTo("DNI:30123456");
    }

    @Test
    void dniOfTenDigitsIsRejected() {
        Map<String, String> row = adult();
        row.put("dni", "1234567890");

        assertThat(codes(row)).containsExactly("DOCUMENT_NUMBER_INVALID");
    }

    @Test
    void passportAcceptsLettersAndDigits() {
        Map<String, String> row = adult();
        row.put("documentType", "PASSPORT");
        row.put("dni", "ab123456");

        assertThat(codes(row)).isEmpty();
        assertThat(definition.naturalKey(row)).isEqualTo("PASSPORT:AB123456");
    }

    @Test
    void unknownDocumentTypeIsRejected() {
        Map<String, String> row = adult();
        row.put("documentType", "LE");

        assertThat(codes(row)).contains("DOCUMENT_TYPE_INVALID");
    }

    @Test
    void missingEmailIsRejectedSoThePersonStaysOutOfTheBatch() {
        Map<String, String> row = adult();
        row.put("email", "");

        assertThat(codes(row)).containsExactly("EMAIL_REQUIRED");
    }

    @Test
    void malformedEmailIsRejected() {
        Map<String, String> row = adult();
        row.put("email", "sin-arroba");

        assertThat(codes(row)).containsExactly("EMAIL_INVALID");
    }

    @Test
    void roleOutsideStudentAndInstructorIsRejected() {
        Map<String, String> row = adult();
        row.put("role", "DIRECTOR");

        assertThat(codes(row)).contains("ROLE_INVALID");
    }

    @Test
    void studentWithoutBirthDateIsRejectedButInstructorIsNot() {
        Map<String, String> student = adult();
        student.put("birthDate", "");
        Map<String, String> instructor = adult();
        instructor.put("birthDate", "");
        instructor.put("role", "INSTRUCTOR");

        assertThat(codes(student)).containsExactly("BIRTH_DATE_REQUIRED");
        assertThat(codes(instructor)).isEmpty();
    }

    @Test
    void twoDigitYearIsAnInvalidDate() {
        Map<String, String> row = adult();
        row.put("birthDate", "18/04/92");

        assertThat(codes(row)).contains("DATE_INVALID");
    }

    @Test
    void minorWithoutGuardianAndEmergencyContactIsRejected() {
        Map<String, String> row = adult();
        row.put("birthDate", LocalDate.now().minusYears(10).toString());

        assertThat(codes(row)).contains("GUARDIAN_REQUIRED_FOR_MINOR", "EMERGENCY_CONTACT_REQUIRED_FOR_MINOR");
    }

    @Test
    void minorWithCompleteContactsIsAcceptedAndGuardianNameIsJoined() {
        Map<String, String> row = adult();
        row.put("birthDate", LocalDate.now().minusYears(10).toString());
        row.put("guardianFirstName", "Claudia");
        row.put("guardianLastName", "Gómez");
        row.put("guardianDocumentType", "DNI");
        row.put("guardianDni", "18.765.432");
        row.put("guardianRelationship", "Madre");
        row.put("guardianPhone", "3514445566");
        row.put("emergencyContactFirstName", "Marcos");
        row.put("emergencyContactLastName", "Gómez");
        row.put("emergencyContactRelationship", "Hermano");
        row.put("emergencyContactPhone", "3519876543");
        when(recorder.record(any())).thenReturn(7L);

        assertThat(codes(row)).isEmpty();
        assertThat(definition.record(row, 1L)).isEqualTo(7L);
    }

    @Test
    void guardianWithPassportIsRejectedBecauseTheModelStoresOnlyDni() {
        Map<String, String> row = adult();
        row.put("guardianDocumentType", "PASSPORT");
        row.put("guardianDni", "AB123456");

        assertThat(codes(row)).contains("GUARDIAN_DOCUMENT_TYPE_UNSUPPORTED", "GUARDIAN_DOCUMENT_INVALID");
    }

    @Test
    void withdrawalBeforeRegistrationIsRejected() {
        Map<String, String> row = adult();
        row.put("withdrawalDate", "2020-01-01");

        assertThat(codes(row)).containsExactly("WITHDRAWAL_BEFORE_REGISTRATION");
    }

    @Test
    void statusWithdrawnIsNotAValidStatus() {
        Map<String, String> row = adult();
        row.put("status", "WITHDRAWN");

        assertThat(codes(row)).containsExactly("STATUS_INVALID");
    }

    @Test
    void textLongerThanTheColumnIsRejectedInsteadOfFailingInTheDatabase() {
        Map<String, String> row = adult();
        row.put("firstName", "x".repeat(256));
        row.put("phone", "1".repeat(31));

        assertThat(codes(row)).containsExactly("TEXT_TOO_LONG", "TEXT_TOO_LONG");
    }

    @Test
    void existingDocumentIsAConflictAndTakenEmailIsAnError() {
        when(recorder.documentExists(DocumentType.DNI, "30123456")).thenReturn(true);

        ImportEntityDefinition.BaseCheck documentCheck = definition.checkAgainstBase(adult());

        assertThat(documentCheck.keyExists()).isTrue();
        assertThat(documentCheck.errors()).extracting(ImportRowError::code).containsExactly("DOCUMENT_ALREADY_REGISTERED");

        when(recorder.documentExists(DocumentType.DNI, "30123456")).thenReturn(false);
        when(recorder.emailExists("lucia@example.com")).thenReturn(true);

        ImportEntityDefinition.BaseCheck emailCheck = definition.checkAgainstBase(adult());

        assertThat(emailCheck.keyExists()).isFalse();
        assertThat(emailCheck.errors()).extracting(ImportRowError::code).containsExactly("EMAIL_ALREADY_REGISTERED");
    }
}
