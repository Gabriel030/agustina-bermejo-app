Feature: Facturación y pagos (POST /students/{id}/invoices, /invoices/{id}/payments)

  # RF-AL-09/RF-AD-08/RF-AL-10: decisión 1 (sin pasarela, facturación interna),
  # decisión 3 (estado de deuda siempre calculado on-demand, nunca persistido),
  # decisión 4 (el recálculo de Invoice.status corre en la misma transacción
  # que el alta del Payment), decisión 14 (exclusivo de Administrativo, ni
  # siquiera el Preceptor tiene acceso).

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def institutionId = fixtures.crearInstitucion()
    * def adminToken = fixtures.tokenParaRol('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenParaRol('PRECEPTOR')
    * def directorToken = fixtures.tokenParaRol('DIRECTOR')
    * def alumno = fixtures.crearAlumnoConfirmado(institutionId)
    * def studentId = alumno.studentId
    * def alumnoToken = alumno.accessToken

  Scenario: Preceptor no tiene acceso a facturación — 403 (decisión 14)
    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + preceptorToken
    And request { concept: 'Cuota', amount: 10000, dueDate: '2026-12-31' }
    When method post
    Then status 403

  Scenario: Ciclo completo — crear factura, ver deuda, pago parcial, exceder saldo, completar pago, cancelar ya no se puede
    # Antes de facturar: sin información de pago
    Given path '/students', studentId, 'payment-standing'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.standing == 'SIN_INFORMACION'

    # Crear factura — 201, PENDING
    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + adminToken
    And request { concept: 'Cuota Marzo', amount: 10000, dueDate: '2026-12-31' }
    When method post
    Then status 201
    And match response.status == 'PENDING'
    * def invoiceId = response.id

    # El alumno ve su propia factura y su deuda — autoservicio
    Given path '/me/invoices'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response == '#[1]'

    Given path '/me/invoices/debt-summary'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response.tieneDeuda == true
    And match response.montoAdeudado == 10000

    # Pago parcial — 201, la factura pasa a PARTIALLY_PAID
    Given path '/invoices', invoiceId, 'payments'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: 4000, method: 'CASH' }
    When method post
    Then status 201

    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def facturaParcial = response.filter(function(x){ return x.id == invoiceId })[0]
    And match facturaParcial.status == 'PARTIALLY_PAID'

    # Intentar pagar más de lo que queda de saldo (6000) — 400, no se registra
    Given path '/invoices', invoiceId, 'payments'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: 9999, method: 'CASH' }
    When method post
    Then status 400

    # Completar el pago — 201, la factura pasa a PAID
    Given path '/invoices', invoiceId, 'payments'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: 6000, method: 'TRANSFER' }
    When method post
    Then status 201

    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def facturaPagada = response.filter(function(x){ return x.id == invoiceId })[0]
    And match facturaPagada.status == 'PAID'

    # Ya no se puede cancelar una factura pagada — 409
    Given path '/invoices', invoiceId, 'cancel'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409

    # El Director puede consultar (R), pero no crear ni pagar — solo se prueba
    # la parte de lectura acá, la de escritura (403 al Director) se cubre en el
    # siguiente escenario para no encadenar de más este.
    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200

  Scenario: Cancelar una factura pendiente — 204, y el Director no puede facturar
    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + directorToken
    And request { concept: 'Cuota Abril', amount: 5000, dueDate: '2026-12-31' }
    When method post
    Then status 403

    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + adminToken
    And request { concept: 'Cuota Abril', amount: 5000, dueDate: '2026-12-31' }
    When method post
    Then status 201
    * def invoiceIdCancelable = response.id

    Given path '/invoices', invoiceIdCancelable, 'cancel'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 204

    Given path '/invoices', invoiceIdCancelable, 'cancel'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 409

  Scenario: Campos faltantes al crear factura — 400
    Given path '/students', studentId, 'invoices'
    And header Authorization = 'Bearer ' + adminToken
    And request { concept: '' }
    When method post
    Then status 400

  Scenario: Factura de un alumno inexistente — 404
    Given path '/students', 999999, 'invoices'
    And header Authorization = 'Bearer ' + adminToken
    And request { concept: 'Cuota', amount: 1000, dueDate: '2026-12-31' }
    When method post
    Then status 404
