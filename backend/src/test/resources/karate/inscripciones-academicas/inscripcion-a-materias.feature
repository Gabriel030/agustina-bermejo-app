Feature: Inscripción a materias del plan de carrera (SubjectSectionEnrollmentController, ProgramEnrollmentController)

  # A diferencia de CourseEnrollment (todo o nada, automático), acá el alumno se
  # inscribe módulo por módulo a comisiones de materias de SU plan de carrera —
  # valida correlatividades, que la materia esté en el plan, cupo con lista de
  # espera FIFO, y permite cambiar de comisión dentro del mismo ciclo.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    * def nombreTerm = 'Ciclo QA materias ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombrePrograma = 'Carrera QA inscripciones ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def nombreMateria = 'Materia QA inscripciones ' + java.lang.System.nanoTime()
    * def codigoMateria = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateria)', code: '#(codigoMateria)' }
    When method post
    Then status 201
    * def subjectId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectId)', curricularYear: 1, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201
    * def programSubjectId = response.id

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión A', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionId = response.id

  Scenario: Ciclo completo — matricular en la carrera, inscribirse a una comisión, ver detalle, dar de baja y reinscribirse reactiva la fila
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    And match response.status == 'ENROLLED'
    * def programEnrollmentId = response.id

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionId)' }
    When method post
    Then status 201
    And match response.status == 'ENROLLED'
    * def sectionEnrollmentId = response.id

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response == '#[1]'

    Given path '/section-enrollments', sectionEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    # Reinscribirse a la MISMA comisión reactiva la fila existente (constraint único), no crea una nueva
    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionId)' }
    When method post
    Then status 201
    And match response.id == sectionEnrollmentId
    And match response.status == 'ENROLLED'

  Scenario: Materia que no está en el plan de la carrera — 400
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    * def nombreMateriaAjena = 'Materia ajena al plan ' + java.lang.System.nanoTime()
    * def codigoAjena = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateriaAjena)', code: '#(codigoAjena)' }
    When method post
    Then status 201
    * def subjectAjenoId = response.id

    Given path '/subjects', subjectAjenoId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión ajena', termId: '#(termId)', capacity: 10 }
    When method post
    Then status 201
    * def sectionAjenaId = response.id

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionAjenaId)' }
    When method post
    Then status 400

  Scenario: Correlatividad no cumplida — 400
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreMateriaAvanzada = 'Materia avanzada ' + java.lang.System.nanoTime()
    * def codigoAvanzada = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateriaAvanzada)', code: '#(codigoAvanzada)' }
    When method post
    Then status 201
    * def subjectAvanzadaId = response.id

    Given path '/programs', programId, 'subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { category: 'MANDATORY', subjectId: '#(subjectAvanzadaId)', curricularYear: 2, curricularTerm: 'FIRST', credits: 5 }
    When method post
    Then status 201
    * def programSubjectAvanzadaId = response.id

    Given path '/program-subjects', programSubjectAvanzadaId, 'prerequisites'
    And header Authorization = 'Bearer ' + adminToken
    And request { prerequisiteProgramSubjectId: '#(programSubjectId)', requirementType: 'APPROVED' }
    When method post
    Then status 201

    Given path '/subjects', subjectAvanzadaId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión avanzada', termId: '#(termId)', capacity: 10 }
    When method post
    Then status 201
    * def sectionAvanzadaId = response.id

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionAvanzadaId)' }
    When method post
    Then status 400

  Scenario: Ya inscripto en otra comisión de la misma materia — 409
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión B', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionBId = response.id

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

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionBId)' }
    When method post
    Then status 409

  Scenario: Cambiar de comisión dentro del mismo ciclo — 200, y a una comisión de otra materia — 400
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión C', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionCId = response.id

    * def nombreMateriaOtra = 'Materia otra para cambio ' + java.lang.System.nanoTime()
    * def codigoOtra = java.util.UUID.randomUUID().toString().substring(0, 4)
    Given path '/subjects'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreMateriaOtra)', code: '#(codigoOtra)' }
    When method post
    Then status 201
    * def subjectOtraId = response.id

    Given path '/subjects', subjectOtraId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión de otra materia', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionOtraMateriaId = response.id

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

    Given path '/section-enrollments', sectionEnrollmentId, 'change-section'
    And header Authorization = 'Bearer ' + adminToken
    And request { newSectionId: '#(sectionCId)' }
    When method post
    Then status 200
    And match response.sectionId == sectionCId
    * def sectionEnrollmentTrasCambioId = response.id

    # El cambio de comisión da de baja la fila origen y crea una fila nueva para el destino
    # (misma materia, mismo Term) — hay que operar sobre la fila nueva, no la original.
    Given path '/section-enrollments', sectionEnrollmentTrasCambioId, 'change-section'
    And header Authorization = 'Bearer ' + adminToken
    And request { newSectionId: '#(sectionOtraMateriaId)' }
    When method post
    Then status 400

  Scenario: Cupo lleno — lista de espera, y la baja del titular promueve al siguiente
    * def alumno1 = fixtures.createConfirmedStudent(fixtures.createInstitution())
    * def alumno2 = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión con cupo 1', termId: '#(termId)', capacity: 1 }
    When method post
    Then status 201
    * def sectionCupoId = response.id

    Given path '/students', alumno1.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollment1Id = response.id

    Given path '/students', alumno2.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollment2Id = response.id

    Given path '/program-enrollments', programEnrollment1Id, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionCupoId)' }
    When method post
    Then status 201
    And match response.status == 'ENROLLED'
    * def sectionEnrollment1Id = response.id

    Given path '/program-enrollments', programEnrollment2Id, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionCupoId)' }
    When method post
    Then status 201
    And match response.status == 'WAITLISTED'
    * def sectionEnrollment2Id = response.id

    Given path '/section-enrollments', sectionEnrollment1Id, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/program-enrollments', programEnrollment2Id, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[0].id == sectionEnrollment2Id
    And match response[0].status == 'ENROLLED'

  Scenario: Ventana de inscripción del ciclo cerrada — 400
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    * def nombreTermCerrado = 'Ciclo QA ventana cerrada ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2020, description: '#(nombreTermCerrado)', type: 'ANUAL', startDate: '2020-01-01T00:00:00', endDate: '2020-12-31T00:00:00', enrollmentStart: '2020-01-01T00:00:00', enrollmentEnd: '2020-02-01T00:00:00' }
    When method post
    Then status 201
    * def termCerradoId = response.id

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión ciclo cerrado', termId: '#(termCerradoId)', capacity: 10 }
    When method post
    Then status 201
    * def sectionCerradaId = response.id

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionCerradaId)' }
    When method post
    Then status 400

  Scenario: ProgramEnrollment inexistente al inscribirse — 404
    Given path '/program-enrollments', 999999, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: '#(sectionId)' }
    When method post
    Then status 404

  Scenario: Sección inexistente al inscribirse — 404
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/program-enrollments', programEnrollmentId, 'section-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { sectionId: 999999 }
    When method post
    Then status 404

  Scenario: Dar de baja una inscripción a materia dos veces — 409
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

    Given path '/section-enrollments', sectionEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/section-enrollments', sectionEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409

  Scenario: Ciclo de ProgramEnrollment — listar por alumno, ver detalle, ver progreso, dar de baja
    * def alumno = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)' }
    When method post
    Then status 201
    * def programEnrollmentId = response.id

    Given path '/students', alumno.studentId, 'program-enrollments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response[*].id contains programEnrollmentId

    Given path '/program-enrollments', programEnrollmentId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200

    Given path '/program-enrollments', programEnrollmentId, 'progress'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200

    Given path '/program-enrollments', programEnrollmentId, 'withdraw'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204
