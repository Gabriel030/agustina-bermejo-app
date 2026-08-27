Feature: Registro independiente (POST /auth/register)

  # Migrado desde qa/bruno/Auth (ver commit que agrega la colección Bruno original) —
  # los criterios de aceptación y las causas raíz de los bugs encontrados quedan acá,
  # no se repiten en el CHANGELOG. Un Scenario = un caso de uso probado contra el
  # backend real (@SpringBootTest RANDOM_PORT + Testcontainers).

  Background:
    * url baseUrl
    * def institutionId = Java.type('com.red_escuela.backend.karate.KarateFixtures').crearInstitucion()
    * def emailUnico = 'karate.registro.' + java.lang.System.nanoTime() + '@example.com'

  Scenario: Happy path — registrarse deja tokens usables de inmediato, sin rol todavía
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'Karate', email: '#(emailUnico)', username: '#(emailUnico)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201
    And match response.accessToken == '#string'
    And match response.refreshToken == '#string'

    Given path '/me'
    And header Authorization = 'Bearer ' + response.accessToken
    When method get
    Then status 200
    And match response.roles == '#[0]'

  Scenario: Username duplicado — 409
    * def username = 'karate.dup.' + java.lang.System.nanoTime()
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'Dup', email: '#(emailUnico)', username: '#(username)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201

    * def otroEmail = 'otro.email.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/auth/register'
    And request { nombre: 'Otra', apellido: 'Persona', email: '#(otroEmail)', username: '#(username)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 409

  # Bug real (encontrado con la colección Bruno original, antes de esta migración):
  # el service solo valida unicidad de `username` de forma explícita — un email
  # duplicado con username DISTINTO dependía del unique constraint de la tabla
  # (uk_person_email), y como Person.id es GenerationType.IDENTITY, el INSERT se
  # ejecuta de inmediato (Hibernate no lo puede diferir), así que el
  # DataIntegrityViolationException escapaba del try/catch original de
  # RegistrationService.registrar. Fix: el try/catch ahora envuelve toda la
  # creación de Person+Student, y GlobalExceptionHandler mapea
  # DataIntegrityViolationException a 409 como red de seguridad. A propósito NO se
  # usó un catch-all de Exception.class: rompía @PreAuthorize (AccessDeniedException
  # se resuelve en el mismo dispatch de Spring MVC que un @RestControllerAdvice).
  Scenario: Email duplicado con username distinto — también 409, no 403 ni 500
    * def usernameA = 'karate.emaildup.a.' + java.lang.System.nanoTime()
    * def usernameB = 'karate.emaildup.b.' + java.lang.System.nanoTime()
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'EmailDupA', email: '#(emailUnico)', username: '#(usernameA)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201

    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'EmailDupB', email: '#(emailUnico)', username: '#(usernameB)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 409

  Scenario Outline: Contraseña inválida — 400 (RF-AUTH-05)
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'PwdInvalida', email: '#(emailUnico)', username: emailUnico, password: '#(password)', institutionId: '#(institutionId)' }
    When method post
    Then status 400

    Examples:
      | password    | motivo                  |
      | Ab1         | menor a min-length (8)  |
      | soloLetras  | sin dígito              |
      | 12345678    | sin letra               |

  Scenario: institutionId inexistente — 404
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'InstInexistente', email: '#(emailUnico)', username: emailUnico, password: '#(passwordValida)', institutionId: 999999 }
    When method post
    Then status 404

  Scenario: Campos faltantes + email con formato inválido — 400
    Given path '/auth/register'
    And request { nombre: '', email: 'no-es-un-email' }
    When method post
    Then status 400
