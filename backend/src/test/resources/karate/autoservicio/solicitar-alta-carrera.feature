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
    And request { name: '#(nombreCarrera)', totalYears: 3, price: { amount: '50000', currency: 'ARS' }, enrollmentFeeAmount: { amount: '50000', currency: 'ARS' }, totalCreditsRequired: 0, teachingMode: 'IN_PERSON' }
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
    * def dniAlumno = '' + (30000000 + java.lang.System.nanoTime() % 60000000)
    * def emailAlumno = 'karate.solicitaralta.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'SolicitaCarrera', email: '#(emailAlumno)', username: '#(emailAlumno)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201
    * def alumnoToken = response.accessToken

    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { dni: '#(dniAlumno)' }
    When method put
    Then status 200

  Scenario: Flujo completo — solicitar, ver PENDING, aprobar, ver APPROVED, rol otorgado
    # Solicitar — 201, PENDING. D-227: sin pago; la matrícula se factura al aprobar.
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    And param programId = programId
    And param cohortId = cohortId
    When method post
    Then status 201
    And match response.status == 'PENDING'
    * def applicationId = response.id

    # Solicitar la misma carrera de nuevo — 409, duplicado pendiente
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    And param programId = programId
    And param cohortId = cohortId
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
    And match propiaAprobada.admissionInvoice.paymentStatus == 'PENDING'
    * def admissionInvoiceId = propiaAprobada.admissionInvoice.id
    * def applicantStudentId = propiaAprobada.studentId

    # Rol ALUMNO otorgado automáticamente al aprobar — criterio de aceptación clave
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.roles contains 'ALUMNO'

    # Sin pagar la matrícula todavía no hay inscripción
    Given path '/me/enrollment-payment-status'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    * def inscripcionesAntesDePagar = response.filter(function(x){ return x.enrollmentType == 'ProgramEnrollment' })
    And match inscripcionesAntesDePagar == '#[0]'

    # Al pagarla, la inscripción queda confirmada
    Given path '/students', applicantStudentId, 'cash-payments', 'preview'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: { amount: '50000', currency: 'ARS' } }
    When method post
    Then status 200
    And match response.coveredInvoices[0].invoiceId == admissionInvoiceId
    * def admissionPreviewFingerprint = response.previewFingerprint

    Given path '/students', applicantStudentId, 'cash-payments'
    And header Authorization = 'Bearer ' + adminToken
    And header Idempotency-Key = 'karate-admission-' + java.lang.System.nanoTime()
    And request { amount: { amount: '50000', currency: 'ARS' }, receiptNumber: 'KARATE-1', previewFingerprint: '#(admissionPreviewFingerprint)' }
    When method post
    Then status 201
    And match response.allocatedInvoices[0].invoiceId == admissionInvoiceId

    # La inscripción se confirma después del commit del pago, de forma asíncrona
    * configure retry = { count: 30, interval: 500 }
    Given path '/me/enrollment-payment-status'
    And header Authorization = 'Bearer ' + alumnoToken
    And retry until response.filter(function(x){ return x.enrollmentType == 'ProgramEnrollment' }).length > 0
    When method get
    Then status 200
    * def matricula = response.filter(function(x){ return x.enrollmentType == 'ProgramEnrollment' })[0]

    Given path '/me/program-enrollments', matricula.enrollmentId, 'invoices'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.enrollmentType == 'PROGRAM'
    And match response.productName == nombreCarrera
