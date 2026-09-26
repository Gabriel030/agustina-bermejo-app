Feature: Caso de uso — Mis matriculaciones e inscripciones a comisión (MeEnrollmentController)

  # Cubre lo que faltaba de MeEnrollmentController más allá de POST /course-enrollments
  # (ya cubierto en inscribirse-a-un-curso.feature): listar mis matriculaciones a
  # carrera, ver mi progreso, inscribirme/darme de baja/cambiar de comisión y listar mis
  # inscripciones a comisión — todo resuelto contra el Student del JWT, nunca un id de
  # la URL (ver comentario de la clase).

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    * def nombreTerm = 'Ciclo QA mis inscripciones ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombrePrograma = 'Carrera QA mis inscripciones ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def nombreMateria = 'Materia QA mis inscripciones ' + java.lang.System.nanoTime()
    * def codigoMateria = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)', code: '#(codigoMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { subjectId: '#(subjectId)', category: 'MANDATORY', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201

    * def evaluationSchemeId = fixtures.createEvaluationScheme()
    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión A', termId: '#(termId)', capacity: 20, evaluationSchemeId: '#(evaluationSchemeId)' }
    When method post
    Then status 201
    * def sectionAId = response.id

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión B', termId: '#(termId)', capacity: 20, evaluationSchemeId: '#(evaluationSchemeId)' }
    When method post
    Then status 201
    * def sectionBId = response.id

    # Alumno matriculado en la carrera (staff) — MeEnrollmentController opera sobre esa
    # matriculación, nunca la crea (eso lo hace ProgramEnrollmentApplicationController
    # o el staff directo)
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())
    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

  Scenario: Listar mis matriculaciones, ver mi progreso, inscribirme/cambiar/listar/darme de baja
    # Mis matriculaciones — incluye la creada por staff
    Given path '/me/program-enrollments'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def propia = response.filter(function(x){ return x.id == programEnrollmentId })[0]
    And match propia != null

    # Mi progreso — 0 créditos acumulados todavía
    Given path '/me/program-enrollments', programEnrollmentId, 'progress'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    And match response.programEnrollmentId == programEnrollmentId
    And match response.creditosAcumulados == 0

    # No puedo ver el progreso de una matriculación ajena — 404
    * def otroAlumno = fixtures.createConfirmedStudent(fixtures.createInstitution())
    Given path '/students', otroAlumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def otraMatriculaId = response.id

    Given path '/me/program-enrollments', otraMatriculaId, 'progress'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 404

    # Inscribirme a la comisión A — 201
    Given path '/me/section-enrollments'
    And header Authorization = 'Bearer ' + alumno.accessToken
    And request { programEnrollmentId: '#(programEnrollmentId)', sectionId: '#(sectionAId)' }
    When method post
    Then status 201
    * def sectionEnrollmentId = response.id

    # Inscribirme usando una matriculación ajena — 404
    Given path '/me/section-enrollments'
    And header Authorization = 'Bearer ' + alumno.accessToken
    And request { programEnrollmentId: '#(otraMatriculaId)', sectionId: '#(sectionBId)' }
    When method post
    Then status 404

    # Aparece en mis inscripciones
    Given path '/me/section-enrollments'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def propiaSeccion = response.filter(function(x){ return x.id == sectionEnrollmentId })[0]
    And match propiaSeccion.sectionId == sectionAId

    # Cambiar de comisión — nuevo id (el service reemplaza la fila, no la muta, mismo
    # comportamiento ya documentado para el endpoint de staff equivalente)
    Given path '/me/section-enrollments', sectionEnrollmentId, 'change-section'
    And header Authorization = 'Bearer ' + alumno.accessToken
    And request { newSectionId: '#(sectionBId)' }
    When method post
    Then status 200
    And match response.sectionId == sectionBId
    * def nuevaSectionEnrollmentId = response.id

    # Un alumno no puede cambiar de comisión una inscripción ajena — 404
    Given path '/me/section-enrollments', nuevaSectionEnrollmentId, 'change-section'
    And header Authorization = 'Bearer ' + otroAlumno.accessToken
    And request { newSectionId: '#(sectionAId)' }
    When method post
    Then status 404

    # Darme de baja — 204
    Given path '/me/section-enrollments', nuevaSectionEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 204

    # Un alumno no puede darse de baja de una inscripción ajena — 404
    * def terceroAlumno = fixtures.createConfirmedStudent(fixtures.createInstitution())
    Given path '/students', terceroAlumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def otraMatricula2Id = response.id

    Given path '/program-enrollments', otraMatricula2Id, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionAId)' }
    When method post
    Then status 201
    * def seccionDeOtroId = response.id

    Given path '/me/section-enrollments', seccionDeOtroId, 'withdraw'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method post
    Then status 404
