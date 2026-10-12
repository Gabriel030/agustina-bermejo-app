Feature: Avisos del docente (AnnouncementController, MeAnnouncementController)

  # El permiso para publicar sale de SubjectSectionInstructor / CourseSubjectSectionInstructor,
  # no de un campo "owner" — un Docente que no dicta la comisión recibe 403. El aviso es de la
  # comisión y no de la persona, así que cualquier instructor de esa comisión puede editarlo.
  # La notificación que llega a la campanita es un PUNTERO: lleva el nombre de la materia y el
  # id del aviso, nunca el cuerpo.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    * def nombreTerm = 'Ciclo QA avisos ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombrePrograma = 'Carrera QA avisos ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def nombreMateria = 'Materia QA avisos ' + java.lang.System.nanoTime()
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

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión QA avisos', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def sectionId = response.id

    * def docente = fixtures.createTeacherWithToken()
    Given path '/program-sections', sectionId, 'instructors'
    And header Authorization = 'Bearer ' + adminToken
    And request { instructorId: '#(docente.instructorId)', role: 'LEAD' }
    When method post
    Then status 201

  Scenario: Happy path — el docente publica y el alumno inscripto lo ve en su feed
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

    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Cambio de aula del jueves', body: 'La clase del jueves se dicta en el Aula 204.', category: 'SCHEDULING', urgent: true }
    When method post
    Then status 201
    * def avisoId = response.id
    And match response.audienceLabel == 'Toda la comisión'
    And match response.urgent == true
    And match response.sectionType == 'PROGRAM'

    # El docente lo ve en el listado de su comisión
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    When method get
    Then status 200
    And match response[0].id == avisoId

    # El alumno lo ve en su feed, con la materia y el docente resueltos
    Given path '/me/announcements'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def mio = response.filter(function(x){ return x.id == avisoId })[0]
    And match mio != null
    And match mio.subjectName == nombreMateria
    And match mio.title == 'Cambio de aula del jueves'

    # La campanita recibió un puntero, no una copia: el mensaje NO trae el cuerpo
    Given path '/me/notifications'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def puntero = response.filter(function(x){ return x.type == 'ANNOUNCEMENT_PUBLISHED' })[0]
    And match puntero != null
    And match puntero.message contains nombreMateria
    And match puntero.message !contains 'Aula 204'

  Scenario: Un docente que no dicta la comisión no puede publicar — 403
    * def otroDocente = fixtures.createTeacherWithToken()

    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + otroDocente.accessToken
    And request { title: 'No debería poder', body: 'Cuerpo cualquiera.', category: 'REMINDER', urgent: false }
    When method post
    Then status 403

  Scenario: Sin sesión — 403, que es lo que devuelve hoy la API (no 401)
    # Se fija el comportamiento REAL, no el esperable. Un request sin token contra un
    # endpoint con @PreAuthorize devuelve 403 y no 401, porque la falta de autenticación
    # llega a la capa de method security como AccessDeniedException. El proyecto ya
    # convivía con esta ambigüedad sin fijarla: PersonControllerTest tiene dos tests
    # llamados "...Returns401OrForbidden" que asiertan is4xxClientError(). Si algún día
    # se unifica en 401, este escenario falla y hay que actualizarlo a propósito.
    Given path '/subject-sections', sectionId, 'announcements'
    And request { title: 'Sin token', body: 'Cuerpo cualquiera.', category: 'REMINDER', urgent: false }
    When method post
    Then status 403

  Scenario: Body inválido — 400, Bean Validation activo
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: '', body: '', category: null, urgent: false }
    When method post
    Then status 400

  Scenario: Un aviso ya vencido no aparece en el feed del alumno
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

    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Ya vencido', body: 'No debería verse.', category: 'REMINDER', urgent: false, expiresAt: '2020-01-01' }
    When method post
    Then status 201
    * def vencidoId = response.id

    Given path '/me/announcements'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def encontrado = response.filter(function(x){ return x.id == vencidoId })
    And match encontrado == []

  Scenario: Un alumno sin inscripciones recibe lista vacía, no un error
    * def solitario = fixtures.createConfirmedStudent(fixtures.createInstitution())

    Given path '/me/announcements'
    And header Authorization = 'Bearer ' + solitario.accessToken
    When method get
    Then status 200
    And match response == []

  Scenario: Editar un aviso lo actualiza y deja updatedAt; eliminarlo lo saca del feed
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

    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Original', body: 'Cuerpo original.', category: 'REMINDER', urgent: false }
    When method post
    Then status 201
    * def avisoId = response.id
    And match response.updatedAt == null

    Given path '/announcements', avisoId
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Corregido', body: 'Cuerpo corregido.', category: 'EVALUATION', urgent: true }
    When method put
    Then status 200
    And match response.title == 'Corregido'
    And match response.category == 'EVALUATION'
    And match response.updatedAt != null

    Given path '/me/announcements'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def corregido = response.filter(function(x){ return x.id == avisoId })[0]
    And match corregido.title == 'Corregido'

    Given path '/announcements', avisoId
    And header Authorization = 'Bearer ' + docente.accessToken
    When method delete
    Then status 204

    Given path '/me/announcements'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def borrado = response.filter(function(x){ return x.id == avisoId })
    And match borrado == []
