package com.red_escuela.backend.demo;

import com.red_escuela.backend.identity.api.DemoStudentIdentityCompleter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Profile("demo")
public class DemoPeopleSeedStep implements DemoSeedStep {
    public static final int STEP_ORDER = 10;

    private static final List<String> FIRST_NAMES = List.of("Lucía", "Mateo", "Valentina", "Santiago", "Camila", "Joaquín",
            "Sofía", "Benjamín", "Martina", "Tomás", "Julieta", "Facundo", "Agustina", "Nicolás", "Milagros", "Lautaro",
            "Florencia", "Ramiro", "Candela", "Bruno");
    private static final List<String> LAST_NAMES = List.of("Gómez", "Fernández", "Rodríguez", "López", "Martínez", "Díaz",
            "Pérez", "Sosa", "Romero", "Álvarez", "Torres", "Ruiz", "Ramírez", "Flores", "Acosta", "Benítez", "Medina",
            "Herrera", "Suárez");
    private static final List<String> TEACHER_NAMES = List.of("Marcela Ibarra", "Gustavo Peralta", "Silvia Domínguez",
            "Ricardo Cabrera", "Patricia Luna", "Alejandro Vega", "Mónica Ríos", "Héctor Paz");
    private static final int MAX_CONSECUTIVE_MISSING_STUDENT_IDS = 10;
    private static final long FIRST_TEACHER_DNI = 30100001L;
    private static final long FIRST_STUDENT_DNI = 40100001L;

    private final DemoSeedSession session;
    private final DemoStudentIdentityCompleter identityCompleter;

    public DemoPeopleSeedStep(DemoSeedSession session, DemoStudentIdentityCompleter identityCompleter) {
        this.session = session;
        this.identityCompleter = identityCompleter;
    }

    @Override
    public int order() {
        return STEP_ORDER;
    }

    @Override
    public String label() {
        return "personas";
    }

    @Override
    public void seed(DemoDataset dataset) {
        long institutionId = findInstitutionId();
        session.studentIdsByUsername().clear();
        for (int index = 0; index < dataset.studentCount(); index++) {
            registerStudentIfMissing(institutionId, index);
            identityCompleter.completeIfMissing(studentUsername(index), String.valueOf(FIRST_STUDENT_DNI + index));
        }
        session.studentIdsByUsername().clear();
        for (int index = 0; index < dataset.teacherCount(); index++) {
            createTeacherIfMissing(index);
        }
    }

    public static String studentUsername(int index) {
        return username(FIRST_NAMES.get(index % FIRST_NAMES.size())) + "." + username(LAST_NAMES.get(index % LAST_NAMES.size()));
    }

    private long findInstitutionId() {
        JsonNode institutions = session.api().getPublic("/institutions");
        if (institutions.isEmpty()) {
            throw new IllegalStateException("No hay una institución: el perfil demo se usa junto con local, que la crea");
        }
        return institutions.get(0).get("id").asLong();
    }

    private void registerStudentIfMissing(long institutionId, int index) {
        String username = studentUsername(index);
        if (findStudentId(session, username) != null) {
            return;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("nombre", FIRST_NAMES.get(index % FIRST_NAMES.size()));
        request.put("apellido", LAST_NAMES.get(index % LAST_NAMES.size()));
        request.put("email", username + "@demo.local");
        request.put("username", username);
        request.put("password", DemoPersona.PASSWORD);
        request.put("institutionId", institutionId);
        session.api().register(request);
    }

    public static Long findStudentId(DemoSeedSession session, String username) {
        Long known = session.studentIdsByUsername().get(username);
        if (known == null) {
            scanStudentIds(session);
        }
        return session.studentIdsByUsername().get(username);
    }

    private static void scanStudentIds(DemoSeedSession session) {
        long highestListedId = 0;
        for (JsonNode listed : session.api().get(DemoPersona.ADMIN, "/students")) {
            long listedId = listed.get("id").asLong();
            session.studentIdsByUsername().put(listed.path("username").asString(""), listedId);
            highestListedId = Math.max(highestListedId, listedId);
        }
        int consecutiveMisses = 0;
        for (long id = highestListedId + 1; consecutiveMisses < MAX_CONSECUTIVE_MISSING_STUDENT_IDS; id++) {
            JsonNode student = session.api().getOrNull(DemoPersona.ADMIN, "/students/" + id);
            if (student == null) {
                consecutiveMisses++;
            } else {
                consecutiveMisses = 0;
                session.studentIdsByUsername().put(student.path("username").asString(""), id);
            }
        }
    }

    private void createTeacherIfMissing(int index) {
        String[] fullName = TEACHER_NAMES.get(index % TEACHER_NAMES.size()).split(" ", 2);
        String email = username(fullName[0]) + "." + username(fullName[1]) + "@demo.local";
        for (JsonNode staff : session.api().get(DemoPersona.ADMIN, "/staff?role=DOCENTE")) {
            if (staff.toString().contains(email)) {
                return;
            }
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("nombre", fullName[0]);
        request.put("apellido", fullName[1]);
        request.put("email", email);
        request.put("dni", String.valueOf(FIRST_TEACHER_DNI + index));
        request.put("role", "DOCENTE");
        session.api().post(DemoPersona.ADMIN, "/staff", request);
    }

    private static String username(String text) {
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
