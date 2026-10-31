Feature: Asistencia y notas (AttendanceController, GradeItemController)

  # Un Docente solo puede cargar asistencia/notas de una comisión propia
  # (SubjectSectionInstructor/CourseSubjectSectionInstructor, no un campo "owner" —
  # NotOwnSectionException, 403). La carga de notas dispara el recálculo automático
  # de status/finalScore de la cursada (GradeItemService.calcularResultado): con
  # examCountExpected=2, tpCountExpected=1, minRegularGrade=4.0, minApprovalGrade=6.0,
  # SIMPLE_AVERAGE, cargar los 3 parciales define REGULAR (promedio >= 4) o FAILED
  # (promedio < 4, corta ahí sin evaluar el final); estando REGULAR, cargar un FINAL
  # define APPROVED o FAILED.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')

    * def nombreTerm = 'Ciclo QA notas ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: '#(java.lang.Math.abs(java.lang.System.nanoTime() % 1000000000))', description: '#(nombreTerm)', type: 'ANNUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombrePrograma = 'Carrera QA notas ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def nombreMateria = 'Materia QA notas ' + java.lang.System.nanoTime()
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
    And request { name: 'Comisión QA notas', termId: '#(termId)', capacity: 20, evaluationSchemeId: '#(evaluationSchemeId)' }
    When method post
    Then status 201
    * def sectionId = response.id

    * def docente = fixtures.createTeacherWithToken()
    Given path '/program-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(docente.instructorId)', role: 'LEAD' }
    When method post
    Then status 201

  Scenario: Ciclo completo — asistencia, tres parciales aprobados llevan a REGULAR, final aprobado lleva a APPROVED
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

    Given path '/program-sections', sectionId, 'attendance'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { classDate: '2026-03-10', entries: [{ enrollmentId: '#(sectionEnrollmentId)', status: 'PRESENT' }] }
    When method post
    Then status 201
    And match response[0].status == 'PRESENT'

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'EXAM', sequenceNumber: 1, date: '2026-04-01T00:00:00', score: 7 }
    When method post
    Then status 201

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'EXAM', sequenceNumber: 2, date: '2026-05-01T00:00:00', score: 8 }
    When method post
    Then status 201

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].status == 'ENROLLED'

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'TP', sequenceNumber: 1, date: '2026-05-15T00:00:00', score: 6 }
    When method post
    Then status 201

    # Tres parciales cargados, promedio (7+8+6)/3 = 7 >= 4 → REGULAR
    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].status == 'REGULAR'

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'FINAL', sequenceNumber: 1, date: '2026-06-01T00:00:00', score: 8 }
    When method post
    Then status 201

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].status == 'APPROVED'

  Scenario: Promedio de parciales insuficiente — la cursada pasa a FAILED sin llegar al final
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

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'EXAM', sequenceNumber: 1, date: '2026-04-01T00:00:00', score: 2 }
    When method post
    Then status 201

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'EXAM', sequenceNumber: 2, date: '2026-05-01T00:00:00', score: 3 }
    When method post
    Then status 201

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'TP', sequenceNumber: 1, date: '2026-05-15T00:00:00', score: 1 }
    When method post
    Then status 201

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].status == 'FAILED'

  Scenario: Un Docente que no integra la comisión no puede cargar asistencia ni notas — 403
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())
    * def otroDocente = fixtures.createTeacherWithToken()

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

    Given path '/program-sections', sectionId, 'attendance'
    And header Authorization = 'Bearer ' + otroDocente.accessToken
    And request { classDate: '2026-03-10', entries: [{ enrollmentId: '#(sectionEnrollmentId)', status: 'PRESENT' }] }
    When method post
    Then status 403

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + otroDocente.accessToken
    And request { type: 'EXAM', sequenceNumber: 1, date: '2026-04-01T00:00:00', score: 7 }
    When method post
    Then status 403

  Scenario: Justificar una ausencia — 200, y listar asistencia/notas (Preceptor/Administrativo/Director)
    * def directorToken = fixtures.tokenForRole('DIRECTOR')
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

    Given path '/program-sections', sectionId, 'attendance'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { classDate: '2026-03-12', entries: [{ enrollmentId: '#(sectionEnrollmentId)', status: 'ABSENT' }] }
    When method post
    Then status 201
    * def attendanceId = response[0].id

    Given path '/attendance', attendanceId, 'justify'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { reason: 'Certificado médico' }
    When method post
    Then status 200
    And match response.status == 'JUSTIFIED'

    Given path '/section-enrollments', sectionEnrollmentId, 'attendance'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response == '#[1]'

    Given path '/section-enrollments', sectionEnrollmentId, 'attendance', 'risk'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response == '#[0]'
