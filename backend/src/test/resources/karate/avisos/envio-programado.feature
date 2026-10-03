Feature: Envío programado de avisos (AnnouncementController, MeAnnouncementController)

  # Un aviso o sale ahora o tiene fecha y hora concretas: no hay borrador sin fecha ni recurrencia.
  # El segmento se resuelve RECIÉN en el momento del envío, así que un aviso programado no tiene
  # destinatarios congelados todavía y el conteo que ve el docente al programar es una estimación.
  # De ahí la asimetría: un programado se puede editar entero (incluida la audiencia) sin notificar
  # a nadie, y uno ya enviado tiene los destinatarios de solo lectura y re-notifica al guardar.
  # El job que hace el envío se testea desde Java (ScheduledAnnouncementDispatchJobTest) porque
  # hay que controlar el reloj: acá va solo el contrato HTTP.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')

    * def nombreTerm = 'Ciclo QA programado ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreTerm)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombrePrograma = 'Carrera QA programado ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombrePrograma)', totalYears: 1, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def nombreMateria = 'Materia QA programado ' + java.lang.System.nanoTime()
    * def codigoMateria = Java.type('com.red_escuela.backend.config.TestSubjectCodes').next()
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

    Given path '/subjects', subjectId, 'sections'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Comisión QA programado', termId: '#(termId)', capacity: 20 }
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

  Scenario: Programar un aviso lo deja SCHEDULED, sin enviar y sin corrimiento de hora
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Parcial del 3 de diciembre', body: 'El parcial se toma en el Aula 110.', category: 'EVALUATION', urgent: false, scheduledFor: '2026-12-01T08:00:00' }
    When method post
    Then status 201
    * def avisoId = response.id
    And match response.status == 'SCHEDULED'
    And match response.sentAt == null
    # La hora vuelve tal cual se mandó: la zona de la aplicación es fija, no la del host
    And match response.scheduledFor == '2026-12-01T08:00:00'

    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    When method get
    Then status 200
    * def mio = response.filter(function(x){ return x.id == avisoId })[0]
    And match mio.status == 'SCHEDULED'
    And match mio.scheduledFor == '2026-12-01T08:00:00'

  Scenario: Publicar sin fecha sale en el momento — SENT con sentAt
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Cambio de aula del jueves', body: 'La clase del jueves se dicta en el Aula 204.', category: 'SCHEDULING', urgent: false }
    When method post
    Then status 201
    And match response.status == 'SENT'
    And match response.sentAt != null
    And match response.scheduledFor == null

  Scenario: Un aviso programado no le llega al alumno ni a su campanita
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Recordatorio de la semana que viene', body: 'Traer la guía impresa.', category: 'REMINDER', urgent: false, scheduledFor: '2026-12-01T08:00:00' }
    When method post
    Then status 201
    * def avisoId = response.id

    Given path '/me/announcements'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def enElFeed = response.filter(function(x){ return x.id == avisoId })
    And match enElFeed == []

    Given path '/me/notifications'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def enLaCampanita = response.filter(function(x){ return x.relatedEntityId == avisoId })
    And match enLaCampanita == []

  Scenario: Programar para un momento que ya pasó — 400
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso con fecha vieja', body: 'Esto nunca se tendría que poder programar.', category: 'ADMIN', urgent: false, scheduledFor: '2020-01-01T08:00:00' }
    When method post
    Then status 400

  Scenario: Editar un aviso programado permite cambiar la audiencia y no notifica a nadie
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso programado', body: 'Cuerpo original del aviso programado.', category: 'REMINDER', urgent: false, scheduledFor: '2026-12-01T08:00:00' }
    When method post
    Then status 201
    * def avisoId = response.id

    Given path '/announcements', avisoId
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso programado corregido', body: 'Cuerpo corregido del aviso programado.', category: 'EVALUATION', urgent: true, scheduledFor: '2026-12-02T09:30:00', audience: { key: 'MUST_TAKE_FINAL' } }
    When method put
    Then status 200
    And match response.status == 'SCHEDULED'
    And match response.scheduledFor == '2026-12-02T09:30:00'
    And match response.audienceLabel == 'Tienen que rendir el final'

    Given path '/me/notifications'
    And header Authorization = 'Bearer ' + alumno.accessToken
    When method get
    Then status 200
    * def enLaCampanita = response.filter(function(x){ return x.relatedEntityId == avisoId })
    And match enLaCampanita == []

  Scenario: Editar la audiencia de un aviso ya enviado — 409
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso ya enviado', body: 'Este aviso salió al publicarse.', category: 'ADMIN', urgent: false }
    When method post
    Then status 201
    * def avisoId = response.id
    And match response.status == 'SENT'

    Given path '/announcements', avisoId
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso ya enviado', body: 'Este aviso salió al publicarse.', category: 'ADMIN', urgent: false, audience: { key: 'MUST_TAKE_FINAL' } }
    When method put
    Then status 409

  Scenario: Reprogramar un aviso ya enviado — 409
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso ya enviado', body: 'Este aviso salió al publicarse.', category: 'ADMIN', urgent: false }
    When method post
    Then status 201
    * def avisoId = response.id

    Given path '/announcements', avisoId
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso ya enviado', body: 'Este aviso salió al publicarse.', category: 'ADMIN', urgent: false, scheduledFor: '2026-12-05T08:00:00' }
    When method put
    Then status 409

  Scenario: Cancelar el envío de un aviso programado lo elimina sin dejar rastro
    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    And request { title: 'Aviso que se va a cancelar', body: 'Nadie lo recibió, así que se borra sin rastro.', category: 'ADMIN', urgent: false, scheduledFor: '2026-12-01T08:00:00' }
    When method post
    Then status 201
    * def avisoId = response.id

    Given path '/announcements', avisoId
    And header Authorization = 'Bearer ' + docente.accessToken
    When method delete
    Then status 204

    Given path '/subject-sections', sectionId, 'announcements'
    And header Authorization = 'Bearer ' + docente.accessToken
    When method get
    Then status 200
    * def quedaAlguno = response.filter(function(x){ return x.id == avisoId })
    And match quedaAlguno == []
