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
    * def institutionId = fixtures.createInstitution()
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')

    # Carrera de prueba (staff) — Fase 6.1: matrícula (enrollmentFeeAmount) es un
    # PriceRevision propio, separado de la cuota mensual (price).
    * def nombreCarrera = 'Karate - Carrera ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCarrera)', totalYears: 3, price: 50000, enrollmentFeeAmount: 50000, totalCreditsRequired: 0 }
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    * def programId = response.id

    * def descripcionPeriodoCarrera = 'Karate - Periodo carrera ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(descripcionPeriodoCarrera)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    # Fase 6.1: solicitar exige una cohorte concreta, colgada de una plantilla anual —
    # la ventana de admisión vive en la cohorte, no en la carrera.
    Given path '/programs', programId, 'year-billing-templates'
    And header Authorization = 'Bearer ' + directorToken
    And request { termId: '#(termId)', recurringFeeInvoicesCount: 10 }
    When method post
    Then status 201
    * def templateId = response.id

    Given path '/year-billing-templates', templateId, 'cohorts'
    And header Authorization = 'Bearer ' + directorToken
    And request { intake: 'FIRST', admissionStart: '2025-01-01', admissionEnd: '2026-12-31', cursadaStart: '2026-03-01' }
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
    And multipart file comprobante = { value: 'contenido de prueba', filename: 'comprobante.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201
    And match response.status == 'PENDING'
    * def applicationId = response.id

    # Solicitar la misma carrera de nuevo — 409, duplicado pendiente
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    And multipart field programId = programId
    And multipart field cohortId = cohortId
    And multipart file comprobante = { value: 'contenido de prueba', filename: 'comprobante.pdf', contentType: 'application/pdf' }
    When method post
    Then status 409
    And match response.code == 'ALREADY_HAS_PENDING_APPLICATION'

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

    # El detalle de cuotas de la carrera dice de qué carrera es
    Given path '/me/billing-standing'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    * def matricula = response.filter(function(x){ return x.enrollmentType == 'ProgramEnrollment' })[0]

    Given path '/me/program-enrollments', matricula.enrollmentId, 'invoices'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.enrollmentType == 'PROGRAM'
    And match response.productName == nombreCarrera
