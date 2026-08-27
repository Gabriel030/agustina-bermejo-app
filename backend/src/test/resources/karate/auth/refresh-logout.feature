Feature: Refresh y Logout (POST /auth/refresh, POST /auth/logout)

  Background:
    * url baseUrl
    * def institutionId = Java.type('com.red_escuela.backend.karate.KarateFixtures').crearInstitucion()
    * def emailUnico = 'karate.refresh.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'Refresh', email: '#(emailUnico)', username: '#(emailUnico)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201
    * def refreshToken = response.refreshToken

  Scenario: Refresh happy path
    Given path '/auth/refresh'
    And request { refreshToken: '#(refreshToken)' }
    When method post
    Then status 200
    And match response.accessToken == '#string'

  Scenario: Refresh con token inválido — 401
    Given path '/auth/refresh'
    And request { refreshToken: 'esto-no-es-un-token-valido' }
    When method post
    Then status 401

  Scenario: Logout happy path — 204
    Given path '/auth/logout'
    And request { refreshToken: '#(refreshToken)' }
    When method post
    Then status 204
    And match response == ''

  # AuthService.logout usa .ifPresent(...) — un token que no existe en refresh_token no
  # tira error, solo no revoca nada. Intencional (anti-enumeración, mismo criterio que
  # login/forgot-password): el cliente nunca puede confirmar por la respuesta si un
  # token existió alguna vez.
  Scenario: Logout con token inválido/inexistente — igual 204 (idempotente a propósito)
    Given path '/auth/logout'
    And request { refreshToken: 'token-que-no-existe-en-la-base' }
    When method post
    Then status 204
