Feature: Mesas de examen (ExamBoardController, ExamBoardRegistrationController, MeExamBoardRegistrationController)

  # ExamBoard se organiza por Subject/CourseSubject + Term, NO por comisión (decisión 7):
  # un alumno REGULAR en la materia rinde en cualquier mesa de esa materia en ese período,
  # sin importar en qué comisión cursó. Inscribirse exige status REGULAR sobre la fila de
  # cursada puntual (scope automático por carrera), materia coincidente, y la mesa abierta
  # y dentro de ventana. Reinscribirse tras una baja reactiva la misma fila.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')

    * def nombreTerm = 'Ciclo QA mesas ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombrePrograma = 'Carrera QA mesas ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def nombreMateria = 'Materia QA mesas ' + java.lang.System.nanoTime()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    * def evaluationSchemeId = fixtures.createEvaluationScheme()
    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión QA mesas', termId: '#(termId)', capacity: 20, evaluationSchemeId: '#(evaluationSchemeId)' }
    When method post
    Then status 201
    * def sectionId = response.id

    * def docente = fixtures.createTeacherWithToken()
    Given path '/program-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(docente.instructorId)', role: 'LEAD' }
    When method post
    Then status 201

  Scenario: Ciclo completo — crear mesa, asignar tribunal, inscribir alumno regular, dar de baja, reinscribirse, cerrar
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

    * eval
    """
    for (var i = 1; i <= 2; i++) {
      var res = karate.call('classpath:karate/helpers/cargar-parcial.feature',
        { baseUrl: baseUrl, docenteToken: docente.accessToken, sectionEnrollmentId: sectionEnrollmentId, seq: i, score: 7 });
    }
    """

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'TP', sequenceNumber: 1, date: '2026-05-15T00:00:00', score: 7, maxScore: 10 }
    When method post
    Then status 201

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].status == 'REGULAR'

    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(termId)', examDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    And match response.status == 'SCHEDULED'
    * def examBoardId = response.id

    Given path '/exam-boards', examBoardId, 'tribunal'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(docente.instructorId)' }
    When method post
    Then status 204

    Given path '/exam-boards', examBoardId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 201
    And match response.status == 'REGISTERED'
    * def registrationId = response.id

    Given path '/exam-boards', examBoardId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    And match response[*].id contains registrationId

    Given path '/exam-board-registrations', registrationId, 'withdraw'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 204

    Given path '/exam-boards', examBoardId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 201
    And match response.id == registrationId
    And match response.status == 'REGISTERED'

    Given path '/exam-boards', examBoardId, 'close'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    # Cerrar un acta ya cerrada es idempotente
    Given path '/exam-boards', examBoardId, 'close'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

  Scenario: Inscribirse sin estar REGULAR — 400
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

    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(termId)', examDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    * def examBoardId = response.id

    Given path '/exam-boards', examBoardId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 400

  Scenario: La materia de la mesa no coincide con la cursada — 400
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

    * eval
    """
    for (var i = 1; i <= 2; i++) {
      karate.call('classpath:karate/helpers/cargar-parcial.feature',
        { baseUrl: baseUrl, docenteToken: docente.accessToken, sectionEnrollmentId: sectionEnrollmentId, seq: i, score: 7 });
    }
    """

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'TP', sequenceNumber: 1, date: '2026-05-15T00:00:00', score: 7, maxScore: 10 }
    When method post
    Then status 201

    * def nombreMateriaOtra = 'Materia QA mesas otra ' + java.lang.System.nanoTime()
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateriaOtra)' }
    When method post
    Then status 201
    * def subjectOtroId = response.id

    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectOtroId)', termId: '#(termId)', examDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    * def examBoardOtroId = response.id

    Given path '/exam-boards', examBoardOtroId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: '#(examBoardOtroId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 400

  Scenario: Mesa cerrada — 409
    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(termId)', examDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    * def examBoardId = response.id

    Given path '/exam-boards', examBoardId, 'close'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

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

    Given path '/exam-boards', examBoardId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 409

  Scenario: Ventana de inscripción cerrada — 400
    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(termId)', examDate: '2026-07-01T00:00:00', registrationStart: '2020-01-01T00:00:00', registrationEnd: '2020-02-01T00:00:00' }
    When method post
    Then status 201
    * def examBoardId = response.id

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

    Given path '/exam-boards', examBoardId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 400

  Scenario: Doble inscripción sin baja — 409, y dar de baja dos veces — 409
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

    * eval
    """
    for (var i = 1; i <= 2; i++) {
      karate.call('classpath:karate/helpers/cargar-parcial.feature',
        { baseUrl: baseUrl, docenteToken: docente.accessToken, sectionEnrollmentId: sectionEnrollmentId, seq: i, score: 7 });
    }
    """

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'TP', sequenceNumber: 1, date: '2026-05-15T00:00:00', score: 7, maxScore: 10 }
    When method post
    Then status 201

    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(termId)', examDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    * def examBoardId = response.id

    Given path '/exam-boards', examBoardId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 201
    * def registrationId = response.id

    Given path '/exam-boards', examBoardId, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 409

    Given path '/exam-board-registrations', registrationId, 'withdraw'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 204

    Given path '/exam-board-registrations', registrationId, 'withdraw'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 409

  Scenario: Term inexistente al crear mesa — 404, mesa inexistente al inscribir — 404
    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: 999999, examDate: '2026-07-01T00:00:00' }
    When method post
    Then status 404

    Given path '/exam-boards', 999999, 'registrations'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { examBoardId: 999999, programSectionEnrollmentId: 1 }
    When method post
    Then status 404

  Scenario: Autoservicio — el alumno se inscribe y se da de baja por /me
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

    * eval
    """
    for (var i = 1; i <= 2; i++) {
      karate.call('classpath:karate/helpers/cargar-parcial.feature',
        { baseUrl: baseUrl, docenteToken: docente.accessToken, sectionEnrollmentId: sectionEnrollmentId, seq: i, score: 7 });
    }
    """

    Given path '/section-enrollments', sectionEnrollmentId, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'TP', sequenceNumber: 1, date: '2026-05-15T00:00:00', score: 7, maxScore: 10 }
    When method post
    Then status 201

    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(termId)', examDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    * def examBoardId = response.id

    Given path '/me/exam-board-registrations'
    And header Authorization = 'Bearer ' + alumno.accessToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollmentId)' }
    When method post
    Then status 201
    * def registrationId = response.id

    Given path '/me/exam-board-registrations', registrationId, 'withdraw'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 204

  Scenario: Autoservicio — un alumno no puede dar de baja la inscripción de otro (404, no 403)
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

    * eval
    """
    for (var i = 1; i <= 2; i++) {
      karate.call('classpath:karate/helpers/cargar-parcial.feature',
        { baseUrl: baseUrl, docenteToken: docente.accessToken, sectionEnrollmentId: sectionEnrollment1Id, seq: i, score: 7 });
    }
    """

    Given path '/section-enrollments', sectionEnrollment1Id, 'grade-items'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { type: 'TP', sequenceNumber: 1, date: '2026-05-15T00:00:00', score: 7, maxScore: 10 }
    When method post
    Then status 201

    Given path '/exam-boards'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', termId: '#(termId)', examDate: '2026-07-01T00:00:00' }
    When method post
    Then status 201
    * def examBoardId = response.id

    Given path '/me/exam-board-registrations'
    And header Authorization = 'Bearer ' + alumno1.accessToken
    And request { examBoardId: '#(examBoardId)', programSectionEnrollmentId: '#(sectionEnrollment1Id)' }
    When method post
    Then status 201
    * def registrationId = response.id

    Given path '/me/exam-board-registrations', registrationId, 'withdraw'
    And header Authorization = 'Bearer ' + alumno2.accessToken
    When method post
    Then status 404
