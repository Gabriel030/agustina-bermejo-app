Feature: Catálogo — Períodos lectivos (TermController)

  # Mismo criterio que carreras/cursos.feature — cubre lo que faltaba de TermController
  # además del alta (ya usada como setup en casi todos los dominios). El campo
  # enrollmentOverride no se expone en TermResponse (ver TermResponse.from) — los
  # endpoints de open/close/clear-override solo se verifican por status code, su efecto
  # real sobre la ventana de inscripción ya está cubierto en el dominio Inscripciones
  # académicas (decisión 5 de la spec de Procesos administrativos).

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def alumnoToken = fixtures.createConfirmedStudent(fixtures.createInstitution()).accessToken

    * def descripcionPeriodo = 'Karate - Periodo catálogo ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2027, description: '#(descripcionPeriodo)', type: 'ANUAL', startDate: '2027-01-01T00:00:00', endDate: '2027-12-31T00:00:00' }
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

    Given path '/terms', termId, 'enrollment', 'close'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/terms', termId, 'enrollment', 'clear-override'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

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
    And request { year: 2027, description: '#(descripcionInvertida)', type: 'ANUAL', startDate: '2027-12-31T00:00:00', endDate: '2027-01-01T00:00:00' }
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
    And request { startDate: '2027-02-01T00:00:00', endDate: '2027-11-30T00:00:00' }
    When method put
    Then status 200
