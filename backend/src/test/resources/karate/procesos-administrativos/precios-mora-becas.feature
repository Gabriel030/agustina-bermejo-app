Feature: Precios, mora y becas (FeeSchedule, Discount, Promotion, PaymentReceipt)

  # Épica 8 Fase 1: solo el Director define precios/mora/becas (regla de negocio
  # explícita). Matrícula (solo carrera) y cuota mensual (carrera o curso) son
  # conceptos de precio independientes. Descuento individual + promoción
  # segmentada se suman (no se toma el mayor, no son excluyentes). El comprobante
  # subido por el Alumno (PaymentReceipt) es un paso previo sin confirmar — recién
  # al aprobarlo se crea el Payment real.

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.crearInstitucion()
    * def directorToken = fixtures.tokenParaRol('DIRECTOR')
    * def adminToken = fixtures.tokenParaRol('ADMINISTRATIVO')
    * def alumno = fixtures.crearAlumnoConfirmado(institutionId)
    * def studentId = alumno.studentId

    * def nombreCarrera = 'Karate - Carrera precios ' + java.lang.System.nanoTime()
    Given path '/programs'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCarrera)', totalYears: 3, totalCreditsRequired: 0 }
    When method post
    Then status 201
    * def programId = response.id

  Scenario: Solo el Director puede crear un FeeSchedule — 403 para Administrativo
    Given path '/fee-schedules'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)', concept: 'MATRICULA', amount: 50000, vigenteDesde: '2026-01-01' }
    When method post
    Then status 403

  Scenario: Un FeeSchedule no puede referenciar programa y curso a la vez, ni ninguno — 400
    Given path '/fee-schedules'
    And header Authorization = 'Bearer ' + directorToken
    And request { concept: 'MATRICULA', amount: 50000, vigenteDesde: '2026-01-01' }
    When method post
    Then status 400

  Scenario: Ciclo completo — matrícula con descuento + promoción sumados, comprobante rechazado y reintento, aprobación crea el Payment real
    # Matrícula vigente desde ayer
    Given path '/fee-schedules'
    And header Authorization = 'Bearer ' + directorToken
    And request { programId: '#(programId)', concept: 'MATRICULA', amount: 50000, vigenteDesde: '2020-01-01' }
    When method post
    Then status 201
    And match response.amount == 50000

    # Beca individual del 20% para este alumno
    Given path '/students', studentId, 'discounts'
    And header Authorization = 'Bearer ' + directorToken
    And request { tipo: 'PORCENTAJE', valor: 20, vigenteDesde: '2020-01-01', motivo: 'Beca por mérito académico' }
    When method post
    Then status 201

    # Promoción segmentada del 10% para toda la carrera
    Given path '/promotions'
    And header Authorization = 'Bearer ' + directorToken
    And request { programId: '#(programId)', tipo: 'PORCENTAJE', valor: 10, vigenteDesde: '2020-01-01' }
    When method post
    Then status 201

    # Generar la factura de matrícula — el monto ya refleja 20% + 30% = 30% de descuento (35000)
    Given path '/students', studentId, 'invoices', 'from-fee-schedule'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)', concept: 'MATRICULA', dueDate: '2026-12-31' }
    When method post
    Then status 201
    And match response.amount == 35000
    And match response.concept contains 'Matrícula'
    * def invoiceId = response.id

    # Subir un comprobante — 201, PENDING_REVIEW
    Given path '/invoices', invoiceId, 'payment-receipts'
    And header Authorization = 'Bearer ' + alumno.accessToken
    And multipart file file = { value: 'contenido de prueba', filename: 'comprobante.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201
    And match response.status == 'PENDING_REVIEW'
    * def receiptId = response.id

    # No se puede subir un segundo comprobante mientras el primero sigue en revisión — 409
    Given path '/invoices', invoiceId, 'payment-receipts'
    And header Authorization = 'Bearer ' + alumno.accessToken
    And multipart file file = { value: 'otro intento', filename: 'comprobante2.pdf', contentType: 'application/pdf' }
    When method post
    Then status 409

    # Rechazar el comprobante — 200, motivo visible, sin crear ningún Payment
    Given path '/payment-receipts', receiptId, 'reject'
    And header Authorization = 'Bearer ' + adminToken
    And request { reason: 'Comprobante ilegible' }
    When method post
    Then status 200
    And match response.status == 'REJECTED'
    And match response.rejectionReason == 'Comprobante ilegible'

    # Reintento sin límite — el primero ya no está en revisión, uno nuevo es válido
    Given path '/invoices', invoiceId, 'payment-receipts'
    And header Authorization = 'Bearer ' + alumno.accessToken
    And multipart file file = { value: 'segundo intento', filename: 'comprobante-v2.pdf', contentType: 'application/pdf' }
    When method post
    Then status 201
    * def segundoReceiptId = response.id

    # Aprobar — 200, crea el Payment real y la factura pasa a PAID
    Given path '/payment-receipts', segundoReceiptId, 'approve'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def facturaPagada = response.filter(function(x){ return x.id == invoiceId })[0]
    And match facturaPagada.status == 'PAID'

    # Aprobar un comprobante ya decidido — 409
    Given path '/payment-receipts', segundoReceiptId, 'approve'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409

  Scenario: Un curso no puede tener matrícula — 400
    * def nombreCurso = 'Karate - Curso precios ' + java.lang.System.nanoTime()
    Given path '/terms'
    And header Authorization = 'Bearer ' + adminToken
    And request { year: 2026, description: '#(nombreCurso)', type: 'ANUAL', startDate: '2026-01-01T00:00:00', endDate: '2026-12-31T00:00:00' }
    When method post
    Then status 201
    * def termId = response.id

    Given path '/courses'
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '#(nombreCurso)', termId: '#(termId)', capacity: 20 }
    When method post
    Then status 201
    * def courseId = response.id

    Given path '/fee-schedules'
    And header Authorization = 'Bearer ' + directorToken
    And request { courseId: '#(courseId)', concept: 'MATRICULA', amount: 10000, vigenteDesde: '2026-01-01' }
    When method post
    Then status 400

  Scenario: Generar factura sin FeeSchedule vigente — 404
    Given path '/students', studentId, 'invoices', 'from-fee-schedule'
    And header Authorization = 'Bearer ' + adminToken
    And request { programId: '#(programId)', concept: 'CUOTA_MENSUAL', dueDate: '2026-12-31' }
    When method post
    Then status 404
