package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.academicstructure.entity.TeachingMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class DemoCatalog {

    public record ProgramSpec(
            String name,
            int totalYears,
            BigDecimal monthlyPrice,
            BigDecimal enrollmentFee,
            List<List<String>> subjectsByYear) {}

    public record CourseSpec(
            String name,
            int termYear,
            int durationMonths,
            BigDecimal price,
            TeachingMode teachingMode,
            String schedule,
            int capacity,
            boolean registrationOpen,
            List<String> subjects) {}

    public static final String EARLY_CHILDHOOD = "Profesorado de Educación Inicial";
    public static final String PRIMARY_TEACHING = "Profesorado de Educación Primaria";
    public static final String BUSINESS_ADMIN = "Tecnicatura Superior en Administración de Empresas";
    public static final String SYSTEMS_ANALYSIS = "Tecnicatura Superior en Análisis de Sistemas";
    public static final String NURSING = "Tecnicatura Superior en Enfermería";
    public static final String ENVIRONMENTAL = "Tecnicatura Superior en Gestión Ambiental";
    public static final String EDUCATION_MANAGEMENT = "Licenciatura en Gestión Educativa";

    public static final List<ProgramSpec> PROGRAMS = List.of(
            new ProgramSpec(EARLY_CHILDHOOD, 3, new BigDecimal("48000"), new BigDecimal("95000"), List.of(
                    List.of("Pedagogía", "Didáctica General", "Psicología Educacional",
                            "Alfabetización Académica", "Práctica Docente I", "Historia de la Educación Argentina"),
                    List.of("Didáctica del Nivel Inicial", "Literatura Infantil", "Expresión Corporal y Musical",
                            "Sujeto de la Educación Inicial", "Práctica Docente II", "Problemáticas Contemporáneas de la Educación"),
                    List.of("Juego y Desarrollo Infantil", "Ciencias Naturales en el Nivel Inicial", "Matemática en el Nivel Inicial",
                            "Educación Sexual Integral", "Práctica Docente III", "Investigación Educativa"))),
            new ProgramSpec(PRIMARY_TEACHING, 3, new BigDecimal("48000"), new BigDecimal("95000"), List.of(
                    List.of("Pedagogía", "Didáctica General", "Psicología Educacional",
                            "Alfabetización Académica", "Práctica Docente I", "Historia de la Educación Argentina"),
                    List.of("Didáctica de la Lengua", "Didáctica de la Matemática", "Didáctica de las Ciencias Sociales",
                            "Sujeto de la Educación Primaria", "Práctica Docente II", "Problemáticas Contemporáneas de la Educación"),
                    List.of("Didáctica de las Ciencias Naturales", "Educación Tecnológica", "Educación Artística",
                            "Educación Sexual Integral", "Práctica Docente III", "Investigación Educativa"))),
            new ProgramSpec(BUSINESS_ADMIN, 3, new BigDecimal("52000"), new BigDecimal("105000"), List.of(
                    List.of("Administración General", "Contabilidad I", "Matemática Aplicada",
                            "Economía", "Inglés Técnico I", "Informática Aplicada"),
                    List.of("Contabilidad II", "Comercialización", "Derecho Comercial",
                            "Costos y Presupuestos", "Inglés Técnico II", "Recursos Humanos"),
                    List.of("Administración Financiera", "Gestión de Calidad", "Comercio Exterior",
                            "Legislación Laboral e Impositiva", "Formulación de Proyectos", "Práctica Profesionalizante"))),
            new ProgramSpec(SYSTEMS_ANALYSIS, 3, new BigDecimal("56000"), new BigDecimal("110000"), List.of(
                    List.of("Programación I", "Arquitectura de Computadoras", "Matemática Discreta",
                            "Inglés Técnico I", "Sistemas de Información", "Informática Aplicada"),
                    List.of("Programación II", "Bases de Datos", "Redes de Datos",
                            "Análisis de Sistemas", "Inglés Técnico II", "Estadística"),
                    List.of("Programación III", "Diseño de Sistemas", "Seguridad Informática",
                            "Gestión de Proyectos de Software", "Desarrollo Web", "Práctica Profesionalizante"))),
            new ProgramSpec(NURSING, 3, new BigDecimal("61000"), new BigDecimal("120000"), List.of(
                    List.of("Anatomía y Fisiología", "Fundamentos de Enfermería", "Microbiología y Parasitología",
                            "Psicología General", "Bioética", "Práctica Hospitalaria I"),
                    List.of("Enfermería del Adulto", "Farmacología", "Nutrición y Dietoterapia",
                            "Salud Pública", "Enfermería Materno Infantil", "Práctica Hospitalaria II"),
                    List.of("Enfermería en Cuidados Críticos", "Enfermería Comunitaria", "Administración de Servicios de Enfermería",
                            "Epidemiología", "Metodología de la Investigación en Salud", "Práctica Hospitalaria III"))),
            new ProgramSpec(ENVIRONMENTAL, 3, new BigDecimal("50000"), new BigDecimal("98000"), List.of(
                    List.of("Ecología General", "Química Ambiental", "Biología",
                            "Matemática Aplicada", "Cartografía y SIG", "Legislación Ambiental"),
                    List.of("Contaminación y Tratamiento de Efluentes", "Gestión de Residuos", "Hidrología",
                            "Evaluación de Impacto Ambiental", "Energías Renovables", "Estadística"),
                    List.of("Auditoría Ambiental", "Ordenamiento Territorial", "Educación Ambiental",
                            "Gestión de Riesgos y Emergencias", "Formulación de Proyectos", "Práctica Profesionalizante"))),
            new ProgramSpec(EDUCATION_MANAGEMENT, 4, new BigDecimal("68000"), new BigDecimal("140000"), List.of(
                    List.of("Teoría de la Organización Escolar", "Política Educativa", "Sociología de la Educación",
                            "Metodología de la Investigación I", "Economía de la Educación", "Alfabetización Académica"),
                    List.of("Gestión Institucional", "Legislación Escolar", "Evaluación Institucional",
                            "Metodología de la Investigación II", "Liderazgo y Equipos de Trabajo", "Tecnologías para la Gestión"),
                    List.of("Planeamiento Educativo", "Gestión Curricular", "Administración de Recursos Educativos",
                            "Comunicación Institucional", "Supervisión y Acompañamiento Docente", "Seminario de Integración"),
                    List.of("Diseño de Políticas Educativas", "Gestión de la Innovación", "Ética y Gobernanza Educativa",
                            "Taller de Tesis I", "Taller de Tesis II", "Práctica Profesional Supervisada"))));

    public static final List<CourseSpec> COURSES = List.of(
            new CourseSpec("Introducción a la Programación", 2026, 3, new BigDecimal("45000"),
                    TeachingMode.ONLINE, "Martes y jueves de 19:00 a 21:00", 60, false,
                    List.of("Fundamentos de Programación")),
            new CourseSpec("Marketing Digital", 2026, 6, new BigDecimal("58000"),
                    TeachingMode.ONLINE, "Lunes y miércoles de 18:30 a 20:30", 50, false,
                    List.of("Estrategia Digital", "Analítica y Métricas")),
            new CourseSpec("Primeros Auxilios y RCP", 2026, 3, new BigDecimal("39000"),
                    TeachingMode.IN_PERSON, "Sábados de 9:00 a 13:00", 30, false,
                    List.of("Soporte Vital Básico")),
            new CourseSpec("Auxiliar en Farmacia", 2026, 6, new BigDecimal("62000"),
                    TeachingMode.IN_PERSON, "Martes y jueves de 18:00 a 21:00", 35, false,
                    List.of("Farmacología Básica", "Gestión de Farmacia")),
            new CourseSpec("Operador de Redes y Soporte IT", 2026, 10, new BigDecimal("85000"),
                    TeachingMode.HYBRID, "Lunes, miércoles y viernes de 19:00 a 22:00", 25, false,
                    List.of("Redes y Cableado", "Sistemas Operativos", "Soporte a Usuarios")),
            new CourseSpec("Inglés para Negocios", 2027, 10, new BigDecimal("72000"),
                    TeachingMode.ONLINE, "Martes y jueves de 20:00 a 21:30", 40, true,
                    List.of("Business English I", "Business English II")),
            new CourseSpec("Community Manager", 2027, 3, new BigDecimal("41000"),
                    TeachingMode.ONLINE, "Sábados de 10:00 a 13:00", 45, true,
                    List.of("Gestión de Redes Sociales")));

    public static final List<String[]> CLASSROOMS = List.of(
            new String[] {"Aula 1", "40"}, new String[] {"Aula 2", "40"},
            new String[] {"Aula 3", "35"}, new String[] {"Aula 5", "35"},
            new String[] {"Aula 7", "30"}, new String[] {"Aula 12", "45"},
            new String[] {"Aula 15", "30"}, new String[] {"Aula 20", "25"},
            new String[] {"Laboratorio 1", "20"}, new String[] {"Laboratorio 2", "20"},
            new String[] {"Aula Magna", "120"}, new String[] {"Sala de Prácticas", "18"});

    public static final Map<String, String> EXTRA_PREREQUISITES = Map.of(
            "Práctica Hospitalaria II", "Práctica Hospitalaria I",
            "Práctica Hospitalaria III", "Práctica Hospitalaria II",
            "Taller de Tesis II", "Taller de Tesis I",
            "Metodología de la Investigación II", "Metodología de la Investigación I",
            "Business English II", "Business English I");

    public static String prerequisiteOf(String subjectName) {
        String explicit = EXTRA_PREREQUISITES.get(subjectName);
        if (explicit != null) {
            return explicit;
        }
        if (subjectName.endsWith(" III")) {
            return subjectName.substring(0, subjectName.length() - 4) + " II";
        }
        if (subjectName.endsWith(" II")) {
            return subjectName.substring(0, subjectName.length() - 3) + " I";
        }
        return null;
    }

    private DemoCatalog() {}
}
