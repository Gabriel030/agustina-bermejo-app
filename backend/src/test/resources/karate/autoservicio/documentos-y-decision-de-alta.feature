Feature: Caso de uso — Documentación y decisión de una solicitud de alta

  # Complementa solicitar-alta-carrera.feature (que ya cubre solicitar/aprobar) con lo
  # que faltaba de ProgramEnrollmentApplicationController: agregarDocumento (multipart,
  # mismo patrón que ProspectController), rechazar (con motivo obligatorio) y
  # listarPendientes.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.createInstitution()
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')

    * def nombreCarrera = 'Karate - Carrera documentos ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCarrera)', totalYears: 3, price: 50000, enrollmentFeeAmount: 50000, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

    * def descripcionPeriodoCarreraDocs = 'Karate - Periodo carrera docs ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(descripcionPeriodoCarreraDocs)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    # Fase 6.1: solicitar exige una cohorte concreta, colgada de una plantilla anual.
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

    # El flujo real de registro (/auth/register) y el criterio de "no otorga rol hasta
    # aprobar" ya están cubiertos en solicitar-alta-carrera.feature — acá alcanza con un
    # alumno vía fixture (mismo criterio de deuda técnica entrada 6: no repetir carga
    # sobre el rate limiter compartido de /auth/register para algo que este feature no
    # está probando).
    * def alumnoDni = '' + (40000000 + java.lang.System.nanoTime() % 50000000)
    * def alumnoToken = fixtures.createConfirmedStudentWithIdentity(institutionId, 'Ana', 'Karate', alumnoDni).accessToken

    # Solicitar — multipart desde Épica 7 Fase 3, comprobante bloqueante.
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    And multipart field programId = programId
    And multipart field cohortId = cohortId
    And multipart file comprobante = { value: 'contenido de prueba', filename: 'comprobante.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201
    * def applicationId = response.id

  Scenario: Agregar documentación propia, no ajena; listar pendientes; rechazar con motivo
    # Sin sesión — 403
    Given path '/me/program-enrollment-applications', applicationId, 'documents'
    And param type = 'DNI_FRENTE'
    And multipart file file = { value: 'contenido de prueba', filename: 'dni.pdf', contentType: 'application/pdf' }
    When method post
    Then status 403

    # El propio alumno puede subir su documento — 201
    Given path '/me/program-enrollment-applications', applicationId, 'documents'
    And header Authorization = 'Bearer ' + alumnoToken
    And param type = 'DNI_FRENTE'
    And multipart file file = { value: 'contenido de prueba', filename: 'dni.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201

    # Otro alumno no puede subir documentación a una solicitud ajena — 404, no 403
    # (mismo criterio de ownership que el resto de /me/..., no revela que existe). No
    # hace falta pasar por /auth/register acá (solo se necesita un token válido de otro
    # alumno) — mismo criterio ya documentado como deuda técnica (entrada 6, rate
    # limiter compartido): usar el fixture de Java en vez del endpoint real cuando el
    # endpoint en sí no es lo que se está probando.
    * def otroAlumno = fixtures.createConfirmedStudent(institutionId)

    Given path '/me/program-enrollment-applications', applicationId, 'documents'
    And header Authorization = 'Bearer ' + otroAlumno.accessToken
    And param type = 'DNI_DORSO'
    And multipart file file = { value: 'contenido de prueba', filename: 'dni-dorso.pdf', contentType: 'application/pdf' }
    When method post
    Then status 404

    # La solicitud aparece en la bandeja de pendientes del staff
    Given path '/program-enrollment-applications'
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    * def pendiente = response.filter(function(x){ return x.id == applicationId })[0]
    And match pendiente.status == 'PENDING'

    # Un Alumno no puede listar pendientes — 403
    Given path '/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 403

    # Rechazar sin motivo — 400 (validación @NotBlank)
    Given path '/program-enrollment-applications', applicationId, 'reject'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { reason: '' }
    When method post
    Then status 400

    # Rechazar con motivo — 200, status REJECTED
    Given path '/program-enrollment-applications', applicationId, 'reject'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { reason: 'Documentación ilegible' }
    When method post
    Then status 200
    And match response.status == 'REJECTED'

    # La bandeja de pendientes ya no la incluye
    Given path '/program-enrollment-applications'
    And header Authorization = 'Bearer ' + preceptorToken
    When method get
    Then status 200
    * def yaNoPendiente = response.filter(function(x){ return x.id == applicationId })
    And match yaNoPendiente == '#[0]'

    # El alumno ve REJECTED en su propia bandeja
    Given path '/me/program-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    * def propiaRechazada = response.filter(function(x){ return x.id == applicationId })[0]
    And match propiaRechazada.status == 'REJECTED'

  Scenario: El comprobante de la solicitud llega a la bandeja con nombre y DNI, y administración puede abrirlo
    Given path '/payment-receipts'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def comprobante = response.filter(function(x){ return x.studentDni == alumnoDni })[0]
    And match comprobante.studentName == 'Ana Karate'
    * def receiptId = comprobante.id

    Given path '/payment-receipts', receiptId, 'file'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match header Content-Type contains 'application/pdf'
    * def contenido = new java.lang.String(responseBytes)
    And match contenido == 'contenido de prueba'

    Given path '/payment-receipts', receiptId, 'file'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 403

    Given path '/payment-receipts', 999999999, 'file'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 404

  Scenario: Rechazar la solicitud saca su comprobante de la bandeja de administración
    Given path '/payment-receipts'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def receiptId = response.filter(function(x){ return x.studentDni == alumnoDni })[0].id

    Given path '/program-enrollment-applications', applicationId, 'reject'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { reason: 'Documentación ilegible' }
    When method post
    Then status 200

    Given path '/payment-receipts'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def sigueEnBandeja = response.filter(function(x){ return x.id == receiptId })
    And match sigueEnBandeja == '#[0]'

  Scenario: El alumno ve los documentos que ya subió, y solo los propios
    Given path '/me/documents'
    When method get
    Then status 403

    Given path '/me/program-enrollment-applications', applicationId, 'documents'
    And header Authorization = 'Bearer ' + alumnoToken
    And param type = 'DNI_FRENTE'
    And multipart file file = { value: 'contenido de prueba', filename: 'dni.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201

    Given path '/me/documents'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response[*].type contains 'DNI_FRENTE'

    * def otroAlumno = fixtures.createConfirmedStudent(institutionId)
    Given path '/me/documents'
    And header Authorization = 'Bearer ' + otroAlumno.accessToken
    When method get
    Then status 200
    And match response == '#[0]'
