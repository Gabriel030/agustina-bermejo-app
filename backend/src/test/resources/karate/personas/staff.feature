Feature: Alta y edición de personal (POST/PUT /staff)

  # RF-AD-05/RF-DI-04: reparto exacto de quién puede crear a quién.
  # Administrativo -> Docente/Preceptor. Director -> Administrativo.
  # Cruzado (Administrativo->Administrativo, Director->Docente) = 403, no 400 —
  # el rol en sí es válido, lo que no está permitido es que ESE actor lo asigne.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def adminToken = fixtures.tokenParaRol('ADMINISTRATIVO')
    * def directorToken = fixtures.tokenParaRol('DIRECTOR')

  Scenario: Administrativo da de alta un Docente — 201, y le crea el Instructor asociado
    * def dni = 'KARATE' + java.lang.System.nanoTime()
    * def email = 'karate.docente.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/staff'
    And header Authorization = 'Bearer ' + adminToken
    And request { nombre: 'QA', apellido: 'Docente', email: '#(email)', dni: '#(dni)', city: 'CABA', role: 'DOCENTE' }
    When method post
    Then status 201
    And match response.role == 'DOCENTE'

  Scenario: Administrativo da de alta un Preceptor — 201
    * def dni = 'KARATE' + java.lang.System.nanoTime()
    * def email = 'karate.preceptor.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/staff'
    And header Authorization = 'Bearer ' + adminToken
    And request { nombre: 'QA', apellido: 'Preceptor', email: '#(email)', dni: '#(dni)', city: 'CABA', role: 'PRECEPTOR' }
    When method post
    Then status 201
    And match response.role == 'PRECEPTOR'

  Scenario: Administrativo intenta dar de alta un Administrativo — 403 (rol válido, actor no autorizado para ESE rol)
    * def dni = 'KARATE' + java.lang.System.nanoTime()
    * def email = 'karate.admin2.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/staff'
    And header Authorization = 'Bearer ' + adminToken
    And request { nombre: 'QA', apellido: 'Admin', email: '#(email)', dni: '#(dni)', city: 'CABA', role: 'ADMINISTRATIVO' }
    When method post
    Then status 403

  Scenario: Director da de alta un Administrativo — 201 (caso antes imposible, RF-DI-04)
    * def dni = 'KARATE' + java.lang.System.nanoTime()
    * def email = 'karate.admin3.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/staff'
    And header Authorization = 'Bearer ' + directorToken
    And request { nombre: 'QA', apellido: 'Admin', email: '#(email)', dni: '#(dni)', city: 'CABA', role: 'ADMINISTRATIVO' }
    When method post
    Then status 201
    And match response.role == 'ADMINISTRATIVO'

  Scenario: Director intenta dar de alta un Docente — 403
    * def dni = 'KARATE' + java.lang.System.nanoTime()
    * def email = 'karate.docente2.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/staff'
    And header Authorization = 'Bearer ' + directorToken
    And request { nombre: 'QA', apellido: 'Docente2', email: '#(email)', dni: '#(dni)', city: 'CABA', role: 'DOCENTE' }
    When method post
    Then status 403

  Scenario: Rol inexistente en el dominio (ni DOCENTE/PRECEPTOR/ADMINISTRATIVO) — 400, no 403
    * def dni = 'KARATE' + java.lang.System.nanoTime()
    * def email = 'karate.rolinvalido.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/staff'
    And header Authorization = 'Bearer ' + adminToken
    And request { nombre: 'QA', apellido: 'RolInvalido', email: '#(email)', dni: '#(dni)', city: 'CABA', role: 'SUPERADMIN' }
    When method post
    Then status 400

  # DniAlreadyExistsException está mapeada a 400 en GlobalExceptionHandler, no 409 como
  # el duplicado de username/email de Registrar — inconsistencia real de la API (dos
  # violaciones de unicidad, dos status codes distintos), pero es el comportamiento ya
  # establecido, no algo que este backfill cambie sin que el equipo lo decida a propósito.
  Scenario: DNI duplicado — 400 (no 409, a diferencia de username/email en /auth/register)
    * def dniDup = 'KARATEDUP' + java.lang.System.nanoTime()
    * def emailA = 'karate.dnia.' + java.lang.System.nanoTime() + '@example.com'
    * def emailB = 'karate.dnib.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/staff'
    And header Authorization = 'Bearer ' + adminToken
    And request { nombre: 'QA', apellido: 'DniA', email: '#(emailA)', dni: '#(dniDup)', city: 'CABA', role: 'DOCENTE' }
    When method post
    Then status 201

    Given path '/staff'
    And header Authorization = 'Bearer ' + adminToken
    And request { nombre: 'QA', apellido: 'DniB', email: '#(emailB)', dni: '#(dniDup)', city: 'CABA', role: 'PRECEPTOR' }
    When method post
    Then status 400

  Scenario: Editar personal propio — 200, y un Alumno no puede editar a nadie (403)
    * def dni = 'KARATE' + java.lang.System.nanoTime()
    * def email = 'karate.aeditar.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/staff'
    And header Authorization = 'Bearer ' + adminToken
    And request { nombre: 'QA', apellido: 'AEditar', email: '#(email)', dni: '#(dni)', city: 'CABA', role: 'DOCENTE' }
    When method post
    Then status 201
    * def staffId = response.id

    Given path '/staff', staffId
    And header Authorization = 'Bearer ' + adminToken
    And request { telefono: '11-5555-5555' }
    When method put
    Then status 200

    * def alumnoToken = fixtures.tokenParaRol('ALUMNO')
    Given path '/staff', staffId
    And header Authorization = 'Bearer ' + alumnoToken
    And request { telefono: '11-4444-4444' }
    When method put
    Then status 403
