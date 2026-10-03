Feature: Perfil propio (GET/PUT /me)

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def alumnoToken = fixtures.tokenForRole('ALUMNO')

  Scenario: Ver el perfil propio — 200
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.email == '#string'

  Scenario: Actualizar datos propios — 200, el cambio se refleja en el siguiente GET
    * def nuevoTelefono = '11-9999-' + java.lang.System.nanoTime()
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { telefono: '#(nuevoTelefono)' }
    When method put
    Then status 200
    And match response.telefono == nuevoTelefono

    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.telefono == nuevoTelefono

  Scenario: Email con formato inválido — 400
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { email: 'no-es-un-email' }
    When method put
    Then status 400

  Scenario: Sin sesión — 403
    Given path '/me'
    When method get
    Then status 403

  Scenario: Dos personas pueden dejar su DNI vacío — el vacío se guarda como sin DNI
    * def otroAlumnoToken = fixtures.tokenForRole('ALUMNO')
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { dni: '' }
    When method put
    Then status 200
    And match response.dni == null

    Given path '/me'
    And header Authorization = 'Bearer ' + otroAlumnoToken
    And request { dni: '' }
    When method put
    Then status 200
    And match response.dni == null
