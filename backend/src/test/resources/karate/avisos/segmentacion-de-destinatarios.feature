Feature: Segmentación de destinatarios (audience-segments, audience-preview)

  # Los conteos de todos los segmentos vienen en UN request, porque el formulario los muestra
  # todos juntos en el selector. El preview es POST y no GET porque la spec es un objeto
  # compuesto y además dejaría datos de desempeño de alumnos en la URL.
  # Un segmento que no alcanza a nadie NO se puede publicar: el docente creería que mandó algo.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    * def nombreTerm = 'Ciclo QA segmentos ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombrePrograma = 'Carrera QA segmentos ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def nombreMateria = 'Materia QA segmentos ' + java.lang.System.nanoTime()
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
    And request { name: 'Comisión QA segmentos', termId: '#(termId)', capacity: 20, evaluationSchemeId: '#(evaluationSchemeId)' }
    When method post
    Then status 201
    * def sectionId = response.id

    * def docente = fixtures.createTeacherWithToken()
    Given path '/program-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(docente.instructorId)', role: 'LEAD' }
    When method post
    Then status 201

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

  Scenario: Los conteos de todos los segmentos vienen en un solo request
    Given path '/subject-sections', sectionId, 'audience-segments'
    And header Authorization = 'Bearer ' + docente.accessToken
    When method get
    Then status 200
    * def todos = response.filter(function(x){ return x.key == 'ALL' })[0]
    And match todos != null
    And match todos.label == 'Toda la comisión'
    And match todos.count == 1
    And match todos.needsInstances == false

    # Los que piden instancias vienen sin conteo y declarando cuántas instancias ofrecer
    * def parciales = response.filter(function(x){ return x.key == 'EXAM_FAILED' })[0]
    And match parciales != null
    And match parciales.needsInstances == true
    And match parciales.count == null
    And match parciales.instanceCount == '#number'

    # Sin clases registradas el segmento de inasistencias no se ofrece: el docente no puede
    # elegir un umbral sobre datos que todavia no existen.
    * def inasistencias = response.filter(function(x){ return x.key == 'ABSENCES' })
    And match inasistencias == []

  Scenario: El preview devuelve el conteo, la etiqueta en castellano y los alumnos con su legajo
    Given path '/subject-sections', sectionId, 'audience-preview'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { key: 'ALL' }
    When method post
    Then status 200
    And match response.count == 1
    And match response.totalInSection == 1
    And match response.label == 'Toda la comisión'
    And match response.students[0].studentId == alumno.studentId
    And match response.students[0].name == '#string'

  Scenario: La etiqueta de un segmento con instancias se arma en castellano
    Given path '/subject-sections', sectionId, 'audience-preview'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { key: 'EXAM_FAILED', instances: [1, 2], quantifier: 'ANY' }
    When method post
    Then status 200
    And match response.label == 'Desaprobaron alguno de: 1° parcial y 2° parcial'
    And match response.count == 0

  Scenario: Con cuantificador ALL la etiqueta cambia y sigue siendo legible
    Given path '/subject-sections', sectionId, 'audience-preview'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { key: 'EXAM_PASSED', instances: [1, 2], quantifier: 'ALL' }
    When method post
    Then status 200
    And match response.label == 'Aprobaron todos de: 1° parcial y 2° parcial'

  Scenario: Publicar a un segmento que no alcanza a nadie — 400
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'A nadie', body: 'No debería salir.', category: 'REMINDER', urgent: false, audience: { key: 'MUST_TAKE_FINAL' } }
    When method post
    Then status 400

  Scenario: Publicar a toda la comisión guarda la etiqueta denormalizada
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'A todos', body: 'Sale a toda la comisión.', category: 'REMINDER', urgent: false, audience: { key: 'ALL' } }
    When method post
    Then status 201
    And match response.audienceLabel == 'Toda la comisión'

    Given path '/me/announcements'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    And match response[0].title == 'A todos'

  Scenario: Un docente que no dicta la comisión no puede ver los segmentos — 403
    * def otroDocente = fixtures.createTeacherWithToken()

    Given path '/subject-sections', sectionId, 'audience-segments'
    And header Authorization = 'Bearer ' + otroDocente.accessToken
    When method get
    Then status 403

  Scenario: Los destinatarios congelados de un aviso enviado se pueden consultar después
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso con destinatarios', body: 'Para ver después a quiénes les llegó.', category: 'ADMIN', urgent: false }
    When method post
    Then status 201
    * def avisoId = response.id

    Given path '/announcements', avisoId, 'recipients'
    And header Authorization = 'Bearer ' + docente.accessToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match each response contains { studentId: '#number', name: '#string' }

  Scenario: Un docente que no dicta la comisión no puede ver los destinatarios — 403
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso con destinatarios', body: 'Para ver después a quiénes les llegó.', category: 'ADMIN', urgent: false }
    When method post
    Then status 201
    * def avisoId = response.id

    * def ajeno = fixtures.createTeacherWithToken()
    Given path '/announcements', avisoId, 'recipients'
    And header Authorization = 'Bearer ' + ajeno.accessToken
    When method get
    Then status 403
