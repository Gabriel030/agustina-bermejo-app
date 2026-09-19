Feature: Recuperar contraseña (POST /auth/forgot-password, /auth/verify-reset-code, /auth/reset-password)

  # El código real solo existe en el email que manda EmailService (NoOpEmailService en el
  # perfil "test", ver AuthService/BrevoEmailService) — Karate no puede leerlo, así que acá
  # solo se cubre el contrato HTTP (status, y que no filtre si el email existe o no). El
  # flujo feliz completo (código correcto → token de intercambio → password nueva → login)
  # ya está cubierto por PasswordResetTest a nivel de AuthService.

  Background:
    * url baseUrl
    * def institutionId = Java.type('com.red_escuela.backend.karate.KarateFixtures').createInstitution()
    * def emailUnico = 'karate.resetpw.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'ResetPw', email: '#(emailUnico)', username: '#(emailUnico)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201

  Scenario: forgot-password con email existente — 202, sin body
    Given path '/auth/forgot-password'
    And request { email: '#(emailUnico)' }
    When method post
    Then status 202
    And match response == ''

  # Anti-enumeración: mismo status y mismo body vacío que con un email real — el cliente
  # nunca puede distinguir los dos casos por la respuesta.
  Scenario: forgot-password con email inexistente — mismo 202, sin body
    Given path '/auth/forgot-password'
    And request { email: 'no-existe.karate@example.com' }
    When method post
    Then status 202
    And match response == ''

  Scenario: verify-reset-code con email inexistente — 401
    Given path '/auth/verify-reset-code'
    And request { email: 'no-existe.karate@example.com', code: '000000' }
    When method post
    Then status 401

  Scenario: verify-reset-code con código incorrecto para un email real — mismo 401
    Given path '/auth/forgot-password'
    And request { email: '#(emailUnico)' }
    When method post
    Then status 202

    Given path '/auth/verify-reset-code'
    And request { email: '#(emailUnico)', code: '000000' }
    When method post
    Then status 401

  Scenario: verify-reset-code sin haber pedido nunca un código — mismo 401
    * def emailSinPedidoDeCodigo = 'karate.resetpw.sinpedido.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'SinPedido', email: '#(emailSinPedidoDeCodigo)', username: '#(emailSinPedidoDeCodigo)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201

    Given path '/auth/verify-reset-code'
    And request { email: '#(emailSinPedidoDeCodigo)', code: '123456' }
    When method post
    Then status 401

  Scenario: reset-password con token de intercambio inválido — 401
    Given path '/auth/reset-password'
    And request { resetToken: 'token-que-no-existe', newPassword: '#(passwordValida)' }
    When method post
    Then status 401
