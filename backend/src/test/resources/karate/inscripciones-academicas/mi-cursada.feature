Feature: Autoservicio de la propia cursada (MeAcademicRecordController)

  # RF-AL-07 extendido: solo lectura del propio alumno, alcance limitado al lado
  # "program" (SubjectSectionEnrollment) — no existe autoservicio equivalente para
  # el lado "course" todavía. Ownership es 404, no 403 (mismo criterio que el resto
  # de /me/...). Usa AttendanceController/GradeItemController (dominio "Evaluación
  # y asistencia", todavía sin backfill propio) solo como setup para tener datos
  # reales que consultar — la cobertura exhaustiva de esos dos controllers queda
  # para cuando se backfillee ese dominio.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    * def nombreTerm = 'Ciclo QA mi cursada ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: '#(java.lang.Math.abs(java.lang.System.nanoTime() % 1000000000))', description: '#(nombreTerm)', type: 'ANNUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombrePrograma = 'Carrera QA mi cursada ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def nombreMateria = 'Materia QA mi cursada ' + java.lang.System.nanoTime()
    * def codigoMateria = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)', code: '#(codigoMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id
    * eval Java.type('com.red_escuela.backend.karate.KarateFixtures').activateSubject(subjectId)

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', category: 'MANDATORY', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    * def evaluationSchemeId = fixtures.createEvaluationScheme()
    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión mi cursada', termId: '#(termId)', capacity: 20, evaluationSchemeId: '#(evaluationSchemeId)' }
    When method post
    Then status 201
    * def sectionId = response.id

    * def docente = fixtures.createTeacherWithToken()
    Given path '/program-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(docente.instructorId)', role: 'LEAD' }
    When method post
    Then status 201

  Scenario: El alumno ve su propia asistencia y sus propias notas
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionId)' }
    When method post
    Then status 201
    * def sectionEnrollmentId = response.id

    # Antes de que el docente cargue algo: listas vacías, no error
    Given path '/me/section-enrollments', sectionEnrollmentId, 'attendance'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    And match response == '#[0]'

    Given path '/me/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    And match response == '#[0]'

    # El docente carga asistencia y una nota
    Given path '/program-sections', sectionId, 'attendance'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { classDate: '2026-03-10', entries: [{ enrollmentId: '#(sectionEnrollmentId)', status: 'PRESENT' }] }
    When method post
    Then status 201

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'EXAM', date: '2026-03-15T00:00:00', score: 8 }
    When method post
    Then status 201

    Given path '/me/section-enrollments', sectionEnrollmentId, 'attendance'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].status == 'PRESENT'

    Given path '/me/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].score == 8

  Scenario: Un alumno no puede ver la cursada de otro — 404, no 403
    * def alumno1 = fixtures.createConfirmedStudent(fixtures.createInstitution())
    * def alumno2 = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/students', alumno1.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollment1Id = response.id

    Given path '/program-enrollments', programEnrollment1Id, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionId)' }
    When method post
    Then status 201
    * def sectionEnrollment1Id = response.id

    Given path '/me/section-enrollments', sectionEnrollment1Id, 'attendance'
    And header Authorization = 'Bearer ' + alumno2.accessToken
    When method get
    Then status 404

    Given path '/me/section-enrollments', sectionEnrollment1Id, 'grade-items'
    And header Authorization = 'Bearer ' + alumno2.accessToken
    When method get
    Then status 404

  Scenario: Cursada inexistente — 404
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/me/section-enrollments', 999999, 'attendance'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 404
