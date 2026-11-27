package com.red_escuela.backend.dataimport;

final class HistoricalDatasetCsv {
    static final String PERSON_HEADER = "documentType,dni,firstName,lastName,role,sex,birthDate,email,phone,nationality,street,city,province,postalCode,legajo,registeredAt,status,withdrawalDate,guardianFirstName,guardianLastName,guardianDocumentType,guardianDni,guardianRelationship,guardianPhone,guardianEmail,emergencyContactFirstName,emergencyContactLastName,emergencyContactRelationship,emergencyContactPhone\n";
    static final String PROGRAM_HEADER = "programName,level,totalYears,totalCreditsRequired,totalHours,teachingMode,status,listPrice,listEnrollmentFee,currency,graduationProfile\n";
    static final String SUBJECT_HEADER = "subjectCode,name,weeklyHours,gradeMode,maxFinalExamAttempts,status\n";
    static final String CURRICULUM_HEADER = "programName,subjectCode,curricularYear,curricularTerm,credits,category,recurrence,sortOrder\n";
    static final String PREREQUISITE_HEADER = "programName,subjectCode,prerequisiteCode,requirementType,minGrade\n";
    static final String TERM_HEADER = "year,termType,description,startDate,endDate,programName\n";
    static final String SECTION_HEADER = "sectionCode,subjectCode,year,termType,name,shift,instructorDni,closedOn,actaNumber,actaBook,actaFolio,programName\n";
    static final String PROGRAM_ENROLLMENT_HEADER = "documentType,dni,programName,enrollmentDate,status,completionDate,withdrawalDate,cohortYear,intake\n";
    static final String SECTION_ENROLLMENT_HEADER = "documentType,dni,programName,sectionCode,attemptNumber,status,finalScore,finalGradeType,finalDate\n";
    static final String GRADE_HEADER = "documentType,dni,sectionCode,type,sequenceNumber,date,score\n";
    static final String ATTENDANCE_HEADER = "documentType,dni,sectionCode,classDate,status\n";
    static final String GRADUATION_HEADER = "documentType,dni,programName,graduationDate,registryNumber,legacyTitleNumber\n";

    private final String suffix;
    private final String subjectPrefix;
    private final int year;
    private final long firstDni;
    private final int studentCount;
    private final int sectionCount;

    HistoricalDatasetCsv(String suffix, String subjectPrefix, int year, long firstDni, int studentCount, int sectionCount) {
        this.suffix = suffix;
        this.subjectPrefix = subjectPrefix;
        this.year = year;
        this.firstDni = firstDni;
        this.studentCount = studentCount;
        this.sectionCount = sectionCount;
    }

    String program() {
        return "Tecnicatura " + suffix;
    }

    String subjectCode(int index) {
        return subjectPrefix + String.format("%02d", index);
    }

    String sectionCode(int index) {
        return "COM-" + suffix + "-" + index;
    }

    String studentDni(int index) {
        return String.valueOf(firstDni + index);
    }

    String instructorDni() {
        return String.valueOf(firstDni + studentCount);
    }

    String persons() {
        StringBuilder csv = new StringBuilder(PERSON_HEADER);
        for (int i = 0; i < studentCount; i++) {
            csv.append("DNI,").append(studentDni(i)).append(",Ana,Pérez,STUDENT,F,1990-01-01,student").append(i).append('.').append(suffix)
                    .append("@example.com,3512345678,Argentina,Av. Colón 1,Córdoba,Córdoba,5000,,").append(year).append("-03-01,ACTIVE,,,,,,,,,,,,\n");
        }
        csv.append("DNI,").append(instructorDni()).append(",Marta,Ríos,INSTRUCTOR,F,1978-11-30,marta.").append(suffix)
                .append("@example.com,3511112233,Argentina,Belgrano 77,Carlos Paz,Córdoba,5152,,,ACTIVE,,,,,,,,,,,,\n");
        return csv.toString();
    }

    String programs() {
        return PROGRAM_HEADER + program() + ",UNDERGRADUATE,3,120,1800,IN_PERSON,ACTIVE,85000,40000,ARS,\n";
    }

    String subjects() {
        StringBuilder csv = new StringBuilder(SUBJECT_HEADER);
        for (int i = 0; i < sectionCount; i++) {
            csv.append(subjectCode(i)).append(",Materia ").append(i).append(' ').append(suffix).append(",4,NUMERIC,3,ACTIVE\n");
        }
        return csv.toString();
    }

    String curriculum() {
        StringBuilder csv = new StringBuilder(CURRICULUM_HEADER);
        for (int i = 0; i < sectionCount; i++) {
            csv.append(program()).append(',').append(subjectCode(i)).append(",1,FIRST,6,MANDATORY,PROMOTIONAL,").append(i + 1).append('\n');
        }
        return csv.toString();
    }

    String prerequisites() {
        return PREREQUISITE_HEADER + program() + "," + subjectCode(1) + "," + subjectCode(0) + ",APPROVED,4\n";
    }

    String terms() {
        return TERM_HEADER + year + ",FIRST_SEMESTER,Primer cuatrimestre," + year + "-03-15,15/07/" + year + "," + program() + "\n";
    }

    String termsWithAnnualCycle() {
        return terms() + year + ",ANNUAL,Año lectivo," + year + "-03-01,15/12/" + year + "," + program() + "\n";
    }

    String sections() {
        StringBuilder csv = new StringBuilder(SECTION_HEADER);
        for (int i = 0; i < sectionCount; i++) {
            csv.append(sectionCode(i)).append(',').append(subjectCode(i)).append(',').append(year).append(",FIRST_SEMESTER,Comisión ")
                    .append(i).append(",MORNING,").append(instructorDni()).append(',').append(year).append("-07-20,,Libro 7,Folio ")
                    .append(i + 1).append(",\n");
        }
        return csv.toString();
    }

    String programEnrollments() {
        StringBuilder csv = new StringBuilder(PROGRAM_ENROLLMENT_HEADER);
        for (int i = 0; i < studentCount; i++) {
            csv.append("DNI,").append(studentDni(i)).append(',').append(program()).append(',').append(year).append("-03-01,ENROLLED,,,").append(year).append(",FIRST\n");
        }
        return csv.toString();
    }

    String sectionEnrollments(int firstStudent, int studentsInSlice) {
        StringBuilder csv = new StringBuilder(SECTION_ENROLLMENT_HEADER);
        for (int i = firstStudent; i < firstStudent + studentsInSlice; i++) {
            for (int s = 0; s < sectionCount; s++) {
                csv.append("DNI,").append(studentDni(i)).append(',').append(program()).append(',').append(sectionCode(s))
                        .append(",1,APPROVED,\"8,50\",PROMOTION,").append(year).append("-07-20\n");
            }
        }
        return csv.toString();
    }
}
