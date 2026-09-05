Feature: Caso de uso — Solicitar alta en carrera

  # Migrado desde qa/bruno/Autoservicio.../Caso de uso - Solicitar alta en carrera.
  # Criterios de aceptación de la spec funcional de Épica 7:
  # 1. Un alumno recién registrado, sin rol, puede solicitar alta a una carrera activa.
  # 2. No puede tener dos solicitudes PENDING a la misma carrera al mismo tiempo.
  # 3. Su bandeja refleja el estado real en todo momento (antes y después de decidir).
  # 4. Un Preceptor puede aprobar; al aprobar, el alumno recibe ALUMNO automáticamente.
  # 5. Un Alumno no puede aprobar su propia solicitud.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.crearInstitucion()
    * def adminToken = fixtures.tokenParaRol('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenParaRol('PRECEPTOR')
    * def directorToken = fixtures.tokenParaRol('DIRECTOR')

    # Carrera de prueba (staff)
    * def nombreCarrera = 'Karate - Carrera ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCarrera)', totalYears: 3, price: 50000, totalCreditsRequired: 0 }
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    * def programId = response.id

    # Épica 7 Fase 3: solicitar exige que el Director haya definido la matrícula
    # (FeeSchedule) de esta carrera — sin esto, crearDesdeFeeSchedule() falla 404.
    Given path '/fee-schedules'
    And header Authorization = 'Bearer ' + directorToken
    And request { programId: '#(programId)', concept: 'MATRICULA', amount: 50000, vigenteDesde: '2020-01-01' }
    When method post
    Then status 201

    # Épica 8 Fase 2: solicitar exige una cohorte concreta — la ventana de admisión
    # vive acá, no en la carrera.
    Given path '/program-cohorts'
    And header Authorization = 'Bearer ' + directorToken
    And request { programId: '#(programId)', label: 'Cohorte de prueba', ordenEnAnio: 1, cursadaStart: '2026-03-01' }
    When method post
    Then status 201
    * def cohortId = response.id

    # Alumno recién registrado, sin sesión previa
    * def emailAlumno = 'karate.solicitaralta.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'SolicitaCarrera', email: '#(emailAlumno)', username: '#(emailAlumno)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201
    * def alumnoToken = response.accessToken

  Scenario: Flujo completo — solicitar, ver PENDING, aprobar, ver APPROVED, rol otorgado
    # Solicitar — 201, PENDING. Épica 7 Fase 3: multipart, el comprobante de la
    # matrícula viaja en la misma request que la solicitud (paso bloqueante).
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    And multipart field programId = programId
    And multipart field cohortId = cohortId
    And multipart file receiptFile = { value: 'contenido de prueba', filename: 'comprobante.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201
    And match response.status == 'PENDING'
    * def applicationId = response.id

    # Solicitar la misma carrera de nuevo — 409, duplicado pendiente
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    And multipart field programId = programId
    And multipart field cohortId = cohortId
    And multipart file receiptFile = { value: 'contenido de prueba', filename: 'comprobante.pdf', contentType: 'application/pdf' }
    When method post
    Then status 409

    # Sin rol todavía — solicitar por sí solo no otorga nada
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.roles == '#[0]'

    # Bandeja refleja PENDING antes de decidir
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    * def propiaPendiente = response.filter(function(x){ return x.id == applicationId })[0]
    And match propiaPendiente.status == 'PENDING'

    # Un Alumno (o alguien sin rol) no puede aprobar su propia solicitud — 403
    Given path '/program-enrollment-applications', applicationId, 'approve'
    And header Authorization = 'Bearer ' + alumnoToken
    When method post
    Then status 403

    # El Preceptor sí puede — 200, APPROVED
    Given path '/program-enrollment-applications', applicationId, 'approve'
    And header Authorization = 'Bearer ' + preceptorToken
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    # Bandeja refleja APPROVED después de decidir — mismo id, no un objeto nuevo
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    * def propiaAprobada = response.filter(function(x){ return x.id == applicationId })[0]
    And match propiaAprobada.status == 'APPROVED'

    # Rol ALUMNO otorgado automáticamente al aprobar — criterio de aceptación clave
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.roles contains 'ALUMNO'
