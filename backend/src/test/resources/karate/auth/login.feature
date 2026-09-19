Feature: Login (POST /auth/login)

  Background:
    * url baseUrl
    * def institutionId = Java.type('com.red_escuela.backend.karate.KarateFixtures').createInstitution()
    * def emailUnico = 'karate.login.' + java.lang.System.nanoTime() + '@example.com'
    # Se registra un usuario propio en vez de depender de las cuentas demo
    # (LocalSeedRunner solo corre bajo @Profile("local"), no en "test") — el feature
    # queda autocontenido sin importar qué perfil tenga activo el backend.
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'Login', email: '#(emailUnico)', username: '#(emailUnico)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201

  Scenario: Happy path
    Given path '/auth/login'
    And request { username: '#(emailUnico)', password: '#(passwordValida)' }
    When method post
    Then status 200
    And match response.accessToken == '#string'
    And match response.refreshToken == '#string'
    And match response.expiresIn == '#number'

  Scenario: Credenciales inválidas — 401, mismo código que usuario inexistente (anti-enumeración)
    Given path '/auth/login'
    And request { username: '#(emailUnico)', password: 'contraseña-incorrecta' }
    When method post
    Then status 401
    And match response == ''

  Scenario: Usuario inexistente — también 401 (RF-AUTH-05: no se puede distinguir de credenciales inválidas)
    Given path '/auth/login'
    And request { username: 'no.existe.karate', password: '#(passwordValida)' }
    When method post
    Then status 401

  Scenario: Body vacío — 400, Bean Validation activo
    Given path '/auth/login'
    And request {}
    When method post
    Then status 400

  Scenario: Tipos incorrectos / null explícito — 400, no 500
    Given path '/auth/login'
    And request { username: 12345, password: null }
    When method post
    Then status 400
