Feature: Cobro en efectivo (POST /students/{id}/cash-payments)

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def dniAlumno = '' + (30000000 + java.lang.System.nanoTime() % 60000000)
    * def alumno = fixtures.createConfirmedStudentWithIdentity(fixtures.createInstitution(), 'QA', 'Cobro', dniAlumno)
    * def alumnoToken = alumno.accessToken
    * def studentId = alumno.studentId
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')
    * def docenteToken = fixtures.tokenForRole('DOCENTE')
    * def invoiceId = fixtures.createIssuedInvoiceWithLedgerDebit(studentId, '80000.00')

  Scenario: Alumno, preceptor and docente cannot register, preview or list a student's cash payments — 403
    Given path '/students', studentId, 'cash-payments', 'preview'
    And header Authorization = 'Bearer ' + alumnoToken
    And request { amount: { amount: '1.00', currency: 'ARS' } }
    When method post
    Then status 403

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + alumnoToken
    And header Idempotency-Key = 'karate-forbidden-1'
    And request { amount: { amount: '1.00', currency: 'ARS' }, previewFingerprint: 'x' }
    When method post
    Then status 403

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + preceptorToken
    And header Idempotency-Key = 'karate-forbidden-2'
    And request { amount: { amount: '1.00', currency: 'ARS' }, previewFingerprint: 'x' }
    When method post
    Then status 403

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + docenteToken
    And header Idempotency-Key = 'karate-forbidden-3'
    And request { amount: { amount: '1.00', currency: 'ARS' }, previewFingerprint: 'x' }
    When method post
    Then status 403

    Given path '/students', studentId, 'cash-payments', 'preview'
    And header Authorization = 'Bearer ' + docenteToken
    And request { amount: { amount: '1.00', currency: 'ARS' } }
    When method post
    Then status 403

    Given path '/students', studentId, 'payments'
    And header Authorization = 'Bearer ' + docenteToken
    When method get
    Then status 403

    Given path '/students', studentId, 'payments'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 403

  Scenario: Preview, register, replay and list a cash payment that leaves a surplus in favor
    Given path '/students', studentId, 'cash-payments', 'preview'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: { amount: '100000.00', currency: 'ARS' } }
    When method post
    Then status 200
    And match response.coveredInvoices == '#[1]'
    And match response.coveredInvoices[0].invoiceId == invoiceId
    And match response.coveredByRemainingCredit == '#[0]'
    And match response.unallocatedAfterwards.amount == '20000.00'
    * def fingerprint = response.previewFingerprint
    * def key = 'karate-cash-' + java.lang.System.nanoTime()

    Given path '/students', studentId, 'payments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response == '#[0]'

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + adminToken
    And header Idempotency-Key = key
    And request { amount: { amount: '100000.00', currency: 'ARS' }, receiptNumber: 'R-0001', previewFingerprint: '#(fingerprint)' }
    When method post
    Then status 201
    And match response.allocatedInvoices == '#[1]'
    And match response.allocatedInvoices[0].invoiceId == invoiceId
    And match response.unallocatedCredit.amount == '20000.00'
    * def paymentId = response.paymentId

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + adminToken
    And header Idempotency-Key = key
    And request { amount: { amount: '100000.00', currency: 'ARS' }, receiptNumber: 'R-0001', previewFingerprint: '#(fingerprint)' }
    When method post
    Then status 201
    And match response.paymentId == paymentId

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + adminToken
    And header Idempotency-Key = key
    And request { amount: { amount: '90000.00', currency: 'ARS' }, receiptNumber: 'R-0001', previewFingerprint: '#(fingerprint)' }
    When method post
    Then status 409
    And match response.code == 'IDEMPOTENCY_KEY_CONFLICT'

    Given path '/students', studentId, 'payments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].id == paymentId

    Given path '/me/payments'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].id == paymentId

  Scenario: A stale preview is rejected with 409 and nothing is written
    Given path '/students', studentId, 'cash-payments', 'preview'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: { amount: '80000.00', currency: 'ARS' } }
    When method post
    Then status 200
    * def staleFingerprint = response.previewFingerprint

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + adminToken
    And header Idempotency-Key = 'karate-stale-' + java.lang.System.nanoTime()
    And request { amount: { amount: '70000.00', currency: 'ARS' }, previewFingerprint: '#(staleFingerprint)' }
    When method post
    Then status 409
    And match response.code == 'CASH_PAYMENT_PREVIEW_OUTDATED'

    Given path '/students', studentId, 'payments'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response == '#[0]'

  Scenario: Validation errors carry their code
    Given path '/students', studentId, 'cash-payments', 'preview'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: { amount: '80000.00', currency: 'ARS' } }
    When method post
    Then status 200
    * def fingerprint = response.previewFingerprint

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: { amount: '80000.00', currency: 'ARS' }, previewFingerprint: '#(fingerprint)' }
    When method post
    Then status 400
    And match response.code == 'IDEMPOTENCY_KEY_INVALID'

    * def future = java.time.LocalDate.now().plusDays(2).toString()
    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + adminToken
    And header Idempotency-Key = 'karate-future-' + java.lang.System.nanoTime()
    And request { amount: { amount: '80000.00', currency: 'ARS' }, paidOn: '#(future)', previewFingerprint: '#(fingerprint)' }
    When method post
    Then status 400
    And match response.code == 'PAYMENT_DATE_IN_FUTURE'

    Given path '/students', 999999999, 'cash-payments', 'preview'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: { amount: '80000.00', currency: 'ARS' } }
    When method post
    Then status 404

  Scenario: Backdating beyond five days is barred for the administrative and needs a reason from the director
    Given path '/students', studentId, 'cash-payments', 'preview'
    And header Authorization = 'Bearer ' + adminToken
    And request { amount: { amount: '80000.00', currency: 'ARS' } }
    When method post
    Then status 200
    * def fingerprint = response.previewFingerprint
    * def old = java.time.LocalDate.now().minusDays(30).toString()

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + adminToken
    And header Idempotency-Key = 'karate-old-admin-' + java.lang.System.nanoTime()
    And request { amount: { amount: '80000.00', currency: 'ARS' }, paidOn: '#(old)', previewFingerprint: '#(fingerprint)' }
    When method post
    Then status 400
    And match response.code == 'CASH_PAYMENT_DATE_TOO_OLD'

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + directorToken
    And header Idempotency-Key = 'karate-old-director-1-' + java.lang.System.nanoTime()
    And request { amount: { amount: '80000.00', currency: 'ARS' }, paidOn: '#(old)', previewFingerprint: '#(fingerprint)' }
    When method post
    Then status 400
    And match response.code == 'CASH_PAYMENT_BACKDATE_REASON_REQUIRED'

    Given path '/students', studentId, 'cash-payments'
    And header Authorization = 'Bearer ' + directorToken
    And header Idempotency-Key = 'karate-old-director-2-' + java.lang.System.nanoTime()
    And request { amount: { amount: '80000.00', currency: 'ARS' }, paidOn: '#(old)', backdateReason: 'Recibo extraviado', previewFingerprint: '#(fingerprint)' }
    When method post
    Then status 201
    And match response.allocatedInvoices[0].invoiceId == invoiceId
    And match response.paidOn == old
