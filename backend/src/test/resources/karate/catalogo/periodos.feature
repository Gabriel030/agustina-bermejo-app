Feature: Catálogo — Períodos lectivos (TermController)

  # Mismo criterio que carreras/cursos.feature — cubre lo que faltaba de TermController
  # además del alta (ya usada como setup en casi todos los dominios). El campo
  # TermResponse expone enrollmentOverride (null, OPEN o CLOSED) tras open/close/clear-override.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def alumnoToken = fixtures.createConfirmedStudent(fixtures.createInstitution()).accessToken

    * def descripcionPeriodo = 'Karate - Periodo catálogo ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: '#(java.lang.Math.abs(java.lang.System.nanoTime() % 1000000000))', description: '#(descripcionPeriodo)', type: 'ANNUAL', startDate: '2027-01-01T00:00:00', endDate: '2027-12-31T00:00:00', enrollmentStart: '2000-01-01T00:00:00', enrollmentEnd: '2999-12-31T00:00:00' }
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    * def termId = response.id

  Scenario: Editar, desactivar, reactivar, listar filtrado, override de ventana, 403 y 404
    * def descripcionEditada = descripcionPeriodo + ' (editado)'
    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    And request { description: '#(descripcionEditada)' }
    When method put
    Then status 200
    And match response.description == descripcionEditada

    # Un Alumno no puede editar ni operar sobre períodos
    Given path '/terms', termId
    And header Authorization = 'Bearer ' + alumnoToken
    And request { description: 'no debería aplicar' }
    When method put
    Then status 403

    Given path '/terms', 999999
    And header Authorization = 'Bearer ' + adminToken
    And request { description: 'no existe' }
    When method put
    Then status 404

    Given path '/terms', termId, 'deactivate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'INACTIVE'

    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And param status = 'INACTIVE'
    When method get
    Then status 200
    * def enInactivos = response.filter(function(x){ return x.id == termId })
    And match enInactivos == '#[1]'

    Given path '/terms', termId, 'activate'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.status == 'ACTIVE'

    # Override manual de ventana de inscripción — abrir, cerrar, quitar override
    Given path '/terms', termId, 'enrollment', 'open'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.enrollmentOverride == 'OPEN'

    Given path '/terms', termId, 'enrollment', 'close'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.enrollmentOverride == 'CLOSED'

    Given path '/terms', termId, 'enrollment', 'clear-override'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.enrollmentOverride == null

    # Sobre un período inexistente — 404
    Given path '/terms', 999999, 'enrollment', 'open'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 404

    Given path '/terms', 999999
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 404

  Scenario: Un período que termina antes de empezar se rechaza al crearlo y al editarlo — 400 con código
    * def descripcionInvertida = 'Karate - Periodo invertido ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2027, description: '#(descripcionInvertida)', type: 'ANNUAL', startDate: '2027-12-31T00:00:00', endDate: '2027-01-01T00:00:00', enrollmentStart: '2000-01-01T00:00:00', enrollmentEnd: '2999-12-31T00:00:00' }
    When method post
    Then status 400
    And match response.code == 'INVALID_TERM_DATES'

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    And request { endDate: '2026-06-30T00:00:00' }
    When method put
    Then status 400
    And match response.code == 'INVALID_TERM_DATES'

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    And request { startDate: '2028-03-01T00:00:00' }
    When method put
    Then status 400
    And match response.code == 'INVALID_TERM_DATES'

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.startDate contains '2027-01-01'
    And match response.endDate contains '2027-12-31'

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    And request { startDate: '2027-02-01T00:00:00', endDate: '2027-11-30T00:00:00', enrollmentStart: '2000-01-01T00:00:00', enrollmentEnd: '2999-12-31T00:00:00' }
    When method put
    Then status 200

  Scenario: Las fechas de inicio y fin del período son obligatorias al crear — 400 con código
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2032, description: 'Karate - Sin fechas', type: 'ANNUAL', enrollmentStart: '2031-12-01T00:00:00', enrollmentEnd: '2032-01-31T00:00:00' }
    When method post
    Then status 400
    And match response.code == 'TERM_DATES_REQUIRED'

    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2032, description: 'Karate - Sin fin', type: 'ANNUAL', startDate: '2032-03-01T00:00:00', enrollmentStart: '2031-12-01T00:00:00', enrollmentEnd: '2032-01-31T00:00:00' }
    When method post
    Then status 400
    And match response.code == 'TERM_DATES_REQUIRED'

  Scenario: La ventana de inscripción es obligatoria al crear y se valida al editar — 400 con código
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2031, description: 'Karate - Sin ventana', type: 'ANNUAL', startDate: '2031-01-01T00:00:00', endDate: '2031-12-31T00:00:00' }
    When method post
    Then status 400
    And match response.code == 'TERM_ENROLLMENT_WINDOW_REQUIRED'

    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2031, description: 'Karate - Ventana incompleta', type: 'ANNUAL', startDate: '2031-01-01T00:00:00', endDate: '2031-12-31T00:00:00', enrollmentStart: '2030-12-01T00:00:00' }
    When method post
    Then status 400
    And match response.code == 'TERM_ENROLLMENT_WINDOW_REQUIRED'

    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2031, description: 'Karate - Ventana invertida', type: 'ANNUAL', startDate: '2031-01-01T00:00:00', endDate: '2031-12-31T00:00:00', enrollmentStart: '2030-12-31T00:00:00', enrollmentEnd: '2030-12-01T00:00:00' }
    When method post
    Then status 400

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    And request { enrollmentStart: '2026-01-01T00:00:00', enrollmentEnd: '2026-02-01T00:00:00' }
    When method put
    Then status 200
    And match response.enrollmentStart == '2026-01-01T00:00:00'
    And match response.enrollmentEnd == '2026-02-01T00:00:00'

    Given path '/terms', termId
    And header Authorization = 'Bearer ' + adminToken
    And request { enrollmentEnd: '2025-12-01T00:00:00' }
    When method put
    Then status 400
