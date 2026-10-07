Feature: Caso de uso — Inscribirse a un curso

  # Épica 7 Fase 3: ya NO matricula en el momento — el pago de la primera cuota es un
  # paso bloqueante, el CourseEnrollment real (y el rol ALUMNO) recién se crean cuando
  # el comprobante se aprueba (PaymentReceiptService.aprobar). El bug real de Épica 7
  # Fase 2 (rol ALUMNO no otorgado en la misma transacción — ver
  # CourseEnrollmentService.matricularComoAutoservicio) sigue cubierto acá, ahora en el
  # momento de la aprobación en vez de la inscripción.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.createInstitution()
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')

    # Periodo + curso de prueba (staff)
    * def descripcionPeriodo = 'Karate - Periodo ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(descripcionPeriodo)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    * def nombreCurso = 'Karate - Curso ' + java.lang.System.nanoTime()
    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', schedule: 'Sáb 10-13hs', capacity: 20, price: { amount: '15000', currency: 'ARS' }, durationMonths: 3 }
    When method post
    Then status 201
    * def courseId = response.id

    # Fase 6.1: CourseService.crearCurso ya genera el PriceRevision(MONTHLY_INSTALLMENT) desde el
    # price de arriba — no hace falta un paso aparte.

    # Alumno recién registrado
    * def emailAlumno = 'karate.inscribirmecurso.' + java.lang.System.nanoTime() + '@example.com'
    Given path '/auth/register'
    And request { nombre: 'QA', apellido: 'InscribeCurso', email: '#(emailAlumno)', username: '#(emailAlumno)', password: '#(passwordValida)', institutionId: '#(institutionId)' }
    When method post
    Then status 201
    * def alumnoToken = response.accessToken

  Scenario: Inscripción con comprobante queda en revisión, aprobar recién ahí otorga ALUMNO y crea el CourseEnrollment
    # Solicitar — 201, PENDING_REVIEW, sin CourseEnrollment todavía
    Given path '/me/course-enrollments'
    And header Authorization = 'Bearer ' + alumnoToken
    And multipart field courseId = courseId
    And multipart file comprobante = { value: 'contenido de prueba', filename: 'comprobante.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201
    And match response.paymentStatus == 'PENDING_REVIEW'
    And match response.courseEnrollmentId == null

    # Sin acceso todavía — ni rol ALUMNO ni CourseEnrollment real.
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.roles == '#[0]'

    # Aparece en "mis solicitudes de curso"
    Given path '/me/course-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    * def propia = response.filter(function(x){ return x.courseId == courseId })[0]
    And match propia.paymentStatus == 'PENDING_REVIEW'

    # Administración aprueba el comprobante — recién ahí se matricula de verdad. La
    # bandeja es global (sin filtro por alumno, ver spec técnica), así que se toma el de
    # id más alto — el propio, porque ningún otro comprobante se crea entre el paso de
    # arriba y este (ejecución secuencial de Karate, sin @Karate.Test en paralelo).
    Given path '/bank-transfer-receipts'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def idsOrdenados = $response[*].id
    * def maxId = Math.max.apply(null, idsOrdenados)
    * def pendiente = response.filter(function(x){ return x.id == maxId })[0]

    Given path '/bank-transfer-receipts', pendiente.id, 'approve'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    # El request que hubiera atrapado el bug real: ALUMNO otorgado en la misma
    # operación de aprobación, sin transacción de test de por medio que lo enmascare.
    Given path '/me'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.roles contains 'ALUMNO'

    Given path '/me/course-enrollment-applications'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    * def propiaAprobada = response.filter(function(x){ return x.courseId == courseId })[0]
    And match propiaAprobada.paymentStatus == 'APPROVED'
    And match propiaAprobada.courseEnrollmentId != null
    And match propiaAprobada.courseEnrollmentStatus == 'ENROLLED'

    # Inscribirme de nuevo al mismo curso (ya matriculado) — 409
    Given path '/me/course-enrollments'
    And header Authorization = 'Bearer ' + alumnoToken
    And multipart field courseId = courseId
    And multipart file comprobante = { value: 'contenido de prueba', filename: 'comprobante2.pdf', contentType: 'application/pdf' }
    When method post
    Then status 409
    And match response.code == 'ALREADY_ENROLLED_IN_COURSE'

  Scenario: Inscribirme a un curso inexistente — 404
    Given path '/me/course-enrollments'
    And header Authorization = 'Bearer ' + alumnoToken
    And multipart field courseId = 999999
    And multipart file comprobante = { value: 'contenido de prueba', filename: 'comprobante.pdf', contentType: 'application/pdf' }
    When method post
    Then status 404
