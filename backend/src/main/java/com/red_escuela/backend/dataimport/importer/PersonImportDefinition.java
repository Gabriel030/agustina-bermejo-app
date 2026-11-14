package com.red_escuela.backend.dataimport.importer;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowError;
import com.red_escuela.backend.dataimport.specialist.ImportEntityDefinition;
import com.red_escuela.backend.dataimport.specialist.ImportValueParser;
import com.red_escuela.backend.identity.api.EmergencyContact;
import com.red_escuela.backend.identity.api.GuardianContact;
import com.red_escuela.backend.identity.api.HistoricalPerson;
import com.red_escuela.backend.identity.api.HistoricalPersonRecorder;
import com.red_escuela.backend.identity.api.HistoricalPersonRole;
import com.red_escuela.backend.identity.api.MinorAgeChecker;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PersonImportDefinition implements ImportEntityDefinition {
    public static final List<String> COLUMNS = List.of("documentType", "dni", "firstName", "lastName", "role", "sex",
            "birthDate", "email", "phone", "nationality", "street", "city", "province", "postalCode", "legajo",
            "registeredAt", "status", "withdrawalDate", "guardianFirstName", "guardianLastName",
            "guardianDocumentType", "guardianDni", "guardianRelationship", "guardianPhone", "guardianEmail",
            "emergencyContactFirstName", "emergencyContactLastName", "emergencyContactRelationship",
            "emergencyContactPhone");

    private static final int MAX_TEXT = 255;
    private static final int MAX_PHONE = 30;

    private final HistoricalPersonRecorder recorder;
    private final MinorAgeChecker minorAgeChecker;

    public PersonImportDefinition(HistoricalPersonRecorder recorder, MinorAgeChecker minorAgeChecker) {
        this.recorder = recorder;
        this.minorAgeChecker = minorAgeChecker;
    }

    @Override
    public ImportEntityType type() {
        return ImportEntityType.PERSONS;
    }

    @Override
    public List<String> columns() {
        return COLUMNS;
    }

    @Override
    public String naturalKey(Map<String, String> payload) {
        DocumentType type = documentTypeOf(payload).orElse(DocumentType.DNI);
        return type + ":" + documentNumberOf(payload, type);
    }

    @Override
    public Map<String, String> uniquenessValues(Map<String, String> payload) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("dni", naturalKey(payload));
        String email = ImportValueParser.cleanText(payload.get("email"));
        if (!email.isEmpty()) {
            values.put("email", email.toLowerCase(Locale.ROOT));
        }
        return values;
    }

    @Override
    public List<ImportRowError> validateFormat(Map<String, String> payload) {
        List<ImportRowError> errors = new ArrayList<>();
        validateDocument(payload, errors);
        requireText(payload, "firstName", "FIRST_NAME_REQUIRED", errors);
        requireText(payload, "lastName", "LAST_NAME_REQUIRED", errors);
        Optional<HistoricalPersonRole> role = roleOf(payload);
        if (role.isEmpty()) {
            errors.add(error("role", "ROLE_INVALID", "El rol debe ser STUDENT o INSTRUCTOR"));
        }
        validateEmail(payload, errors);
        validateLengths(payload, errors);
        boolean student = role.map(r -> r == HistoricalPersonRole.STUDENT).orElse(false);
        Optional<LocalDate> birthDate = validateDate(payload, "birthDate", errors);
        birthDate.ifPresent(date -> {
            if (date.isAfter(ApplicationTime.today())) {
                errors.add(error("birthDate", "BIRTH_DATE_IN_FUTURE", "La fecha de nacimiento es futura"));
            }
        });
        if (student && text(payload, "birthDate").isEmpty()) {
            errors.add(error("birthDate", "BIRTH_DATE_REQUIRED", "La fecha de nacimiento es obligatoria para alumnos"));
        }
        validateStatus(payload, errors);
        if (student) {
            validateStudentDates(payload, errors);
            if (birthDate.isPresent() && minorAgeChecker.isMinor(birthDate.get())) {
                requireMinorContacts(payload, errors);
            }
            validateGuardianDocument(payload, errors);
        }
        return errors;
    }

    @Override
    public BaseCheck checkAgainstBase(Map<String, String> payload) {
        DocumentType type = documentTypeOf(payload).orElse(DocumentType.DNI);
        String number = documentNumberOf(payload, type);
        if (recorder.documentExists(type, number)) {
            return new BaseCheck(true, List.of(error("dni", "DOCUMENT_ALREADY_REGISTERED",
                    "Ya existe una persona con ese documento")));
        }
        if (recorder.emailExists(text(payload, "email"))) {
            return new BaseCheck(false, List.of(error("email", "EMAIL_ALREADY_REGISTERED",
                    "El correo ya está registrado")));
        }
        return new BaseCheck(false, List.of());
    }

    @Override
    public Long record(Map<String, String> payload, Long institutionId) {
        DocumentType type = documentTypeOf(payload).orElse(DocumentType.DNI);
        HistoricalPersonRole role = roleOf(payload).orElseThrow();
        boolean student = role == HistoricalPersonRole.STUDENT;
        LocalDate registeredOn = student ? dateOf(payload, "registeredAt") : null;
        LocalDate withdrawnOn = student ? dateOf(payload, "withdrawalDate") : null;
        HistoricalPerson person = new HistoricalPerson(institutionId, role, type, documentNumberOf(payload, type),
                text(payload, "firstName"), text(payload, "lastName"), text(payload, "email").toLowerCase(Locale.ROOT),
                blankToNull(text(payload, "sex")), dateOf(payload, "birthDate"), blankToNull(text(payload, "phone")),
                blankToNull(text(payload, "nationality")), blankToNull(text(payload, "street")),
                blankToNull(text(payload, "city")), blankToNull(text(payload, "province")),
                blankToNull(text(payload, "postalCode")), registeredOn, !"INACTIVE".equals(statusOf(payload)),
                withdrawnOn, student ? guardianOf(payload) : null, student ? emergencyContactOf(payload) : null);
        return recorder.record(person);
    }

    private GuardianContact guardianOf(Map<String, String> payload) {
        String fullName = join(text(payload, "guardianFirstName"), text(payload, "guardianLastName"));
        String document = ImportValueParser.cleanDocumentNumber(payload.get("guardianDni"));
        if (fullName.isEmpty() && document.isEmpty() && text(payload, "guardianRelationship").isEmpty()
                && text(payload, "guardianPhone").isEmpty() && text(payload, "guardianEmail").isEmpty()) {
            return null;
        }
        return new GuardianContact(blankToNull(fullName), blankToNull(document),
                blankToNull(text(payload, "guardianRelationship")), blankToNull(text(payload, "guardianPhone")),
                blankToNull(text(payload, "guardianEmail")));
    }

    private EmergencyContact emergencyContactOf(Map<String, String> payload) {
        String name = join(text(payload, "emergencyContactFirstName"), text(payload, "emergencyContactLastName"));
        if (name.isEmpty() && text(payload, "emergencyContactRelationship").isEmpty()
                && text(payload, "emergencyContactPhone").isEmpty()) {
            return null;
        }
        return new EmergencyContact(blankToNull(name), blankToNull(text(payload, "emergencyContactRelationship")),
                blankToNull(text(payload, "emergencyContactPhone")));
    }

    private void validateDocument(Map<String, String> payload, List<ImportRowError> errors) {
        String rawType = text(payload, "documentType");
        Optional<DocumentType> type = documentTypeOf(payload);
        if (!rawType.isEmpty() && type.isEmpty()) {
            errors.add(error("documentType", "DOCUMENT_TYPE_INVALID", "El tipo de documento debe ser DNI, PASSPORT u OTHER"));
            return;
        }
        DocumentType resolved = type.orElse(DocumentType.DNI);
        String number = documentNumberOf(payload, resolved);
        if (number.isEmpty()) {
            errors.add(error("dni", "DOCUMENT_NUMBER_REQUIRED", "El número de documento es obligatorio"));
        } else if (!resolved.accepts(number)) {
            errors.add(error("dni", "DOCUMENT_NUMBER_INVALID", "El número de documento no tiene un formato válido para " + resolved.label()));
        }
    }

    private void validateEmail(Map<String, String> payload, List<ImportRowError> errors) {
        String email = text(payload, "email");
        if (email.isEmpty()) {
            errors.add(error("email", "EMAIL_REQUIRED", "El correo es obligatorio; quien no tiene queda fuera del lote"));
            return;
        }
        try {
            EmailAddress.of(email);
        } catch (RuntimeException e) {
            errors.add(error("email", "EMAIL_INVALID", "El correo no tiene un formato válido"));
        }
    }

    private void validateLengths(Map<String, String> payload, List<ImportRowError> errors) {
        for (String column : COLUMNS) {
            int max = column.endsWith("hone") ? MAX_PHONE : MAX_TEXT;
            if (text(payload, column).length() > max) {
                errors.add(error(column, "TEXT_TOO_LONG", "El texto supera " + max + " caracteres"));
            }
        }
    }

    private void validateStatus(Map<String, String> payload, List<ImportRowError> errors) {
        String status = statusOf(payload);
        if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
            errors.add(error("status", "STATUS_INVALID", "El estado debe ser ACTIVE o INACTIVE"));
        }
    }

    private void validateStudentDates(Map<String, String> payload, List<ImportRowError> errors) {
        Optional<LocalDate> registered = validateDate(payload, "registeredAt", errors);
        Optional<LocalDate> withdrawn = validateDate(payload, "withdrawalDate", errors);
        LocalDate today = ApplicationTime.today();
        registered.filter(date -> date.isAfter(today))
                .ifPresent(date -> errors.add(error("registeredAt", "REGISTERED_AT_IN_FUTURE", "La fecha de ingreso es futura")));
        withdrawn.filter(date -> date.isAfter(today))
                .ifPresent(date -> errors.add(error("withdrawalDate", "WITHDRAWAL_DATE_IN_FUTURE", "La fecha de baja es futura")));
        if (registered.isPresent() && withdrawn.isPresent() && withdrawn.get().isBefore(registered.get())) {
            errors.add(error("withdrawalDate", "WITHDRAWAL_BEFORE_REGISTRATION", "La baja es anterior al ingreso"));
        }
    }

    private void requireMinorContacts(Map<String, String> payload, List<ImportRowError> errors) {
        requireText(payload, "guardianFirstName", "GUARDIAN_REQUIRED_FOR_MINOR", errors);
        requireText(payload, "guardianLastName", "GUARDIAN_REQUIRED_FOR_MINOR", errors);
        requireText(payload, "guardianDni", "GUARDIAN_REQUIRED_FOR_MINOR", errors);
        requireText(payload, "guardianRelationship", "GUARDIAN_REQUIRED_FOR_MINOR", errors);
        requireText(payload, "guardianPhone", "GUARDIAN_REQUIRED_FOR_MINOR", errors);
        requireText(payload, "emergencyContactFirstName", "EMERGENCY_CONTACT_REQUIRED_FOR_MINOR", errors);
        requireText(payload, "emergencyContactLastName", "EMERGENCY_CONTACT_REQUIRED_FOR_MINOR", errors);
        requireText(payload, "emergencyContactRelationship", "EMERGENCY_CONTACT_REQUIRED_FOR_MINOR", errors);
        requireText(payload, "emergencyContactPhone", "EMERGENCY_CONTACT_REQUIRED_FOR_MINOR", errors);
    }

    private void validateGuardianDocument(Map<String, String> payload, List<ImportRowError> errors) {
        String type = text(payload, "guardianDocumentType");
        if (!type.isEmpty() && !type.equalsIgnoreCase("DNI")) {
            errors.add(error("guardianDocumentType", "GUARDIAN_DOCUMENT_TYPE_UNSUPPORTED", "El documento del responsable debe ser DNI"));
        }
        String number = ImportValueParser.cleanDocumentNumber(payload.get("guardianDni"));
        if (!number.isEmpty() && !DocumentType.DNI.accepts(number)) {
            errors.add(error("guardianDni", "GUARDIAN_DOCUMENT_INVALID", "El DNI del responsable debe tener 7 u 8 dígitos"));
        }
        String guardianEmail = text(payload, "guardianEmail");
        if (!guardianEmail.isEmpty()) {
            try {
                EmailAddress.of(guardianEmail);
            } catch (RuntimeException e) {
                errors.add(error("guardianEmail", "GUARDIAN_EMAIL_INVALID", "El correo del responsable no es válido"));
            }
        }
    }

    private Optional<LocalDate> validateDate(Map<String, String> payload, String column, List<ImportRowError> errors) {
        try {
            return ImportValueParser.parseDate(payload.get(column));
        } catch (IllegalArgumentException e) {
            errors.add(error(column, "DATE_INVALID", "La fecha debe ser yyyy-MM-dd o dd/MM/yyyy"));
            return Optional.empty();
        }
    }

    private void requireText(Map<String, String> payload, String column, String code, List<ImportRowError> errors) {
        if (text(payload, column).isEmpty()) {
            errors.add(error(column, code, "La columna " + column + " es obligatoria"));
        }
    }

    private Optional<DocumentType> documentTypeOf(Map<String, String> payload) {
        String raw = text(payload, "documentType");
        if (raw.isEmpty()) {
            return Optional.of(DocumentType.DNI);
        }
        try {
            return Optional.of(DocumentType.valueOf(raw.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<HistoricalPersonRole> roleOf(Map<String, String> payload) {
        try {
            return Optional.of(HistoricalPersonRole.valueOf(text(payload, "role").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String documentNumberOf(Map<String, String> payload, DocumentType type) {
        String cleaned = ImportValueParser.cleanDocumentNumber(payload.get("dni"));
        return type == DocumentType.DNI ? cleaned : cleaned.toUpperCase(Locale.ROOT);
    }

    private String statusOf(Map<String, String> payload) {
        String status = text(payload, "status").toUpperCase(Locale.ROOT);
        return status.isEmpty() ? "ACTIVE" : status;
    }

    private LocalDate dateOf(Map<String, String> payload, String column) {
        try {
            return ImportValueParser.parseDate(payload.get(column)).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(Map<String, String> payload, String column) {
        return ImportValueParser.cleanText(payload.get(column));
    }

    private static String join(String first, String last) {
        return (first + " " + last).strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static ImportRowError error(String column, String code, String message) {
        return new ImportRowError(column, code, message);
    }
}
