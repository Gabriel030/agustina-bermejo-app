Feature: Subida de comprobante de transferencia (POST /me/bank-transfer-receipts y /students/{id}/bank-transfer-receipts)

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def dniAlumno = '' + (30000000 + java.lang.System.nanoTime() % 60000000)
    * def alumno = fixtures.createConfirmedStudentWithIdentity(fixtures.createInstitution(), 'QA', 'Transferencia', dniAlumno)
    * def alumnoToken = alumno.accessToken
    * def studentId = alumno.studentId
    * def adminToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def preceptorToken = fixtures.tokenForRole('PRECEPTOR')
    * def today = '' + java.time.LocalDate.now()
    * def invoiceId = fixtures.createIssuedInvoiceWithLedgerDebit(studentId, '80000.00')
    * def pdf = '%PDF-1.4 comprobante de prueba'

  Scenario: Student uploads, staff approves, the payment is created once and the invoice ends paid
    * def operationNumber = 'KAR-' + java.lang.System.nanoTime()
    Given path '/me/bank-transfer-receipts'
    And header Authorization = 'Bearer ' + alumnoToken
    And header Idempotency-Key = 'karate-bt-' + operationNumber
    And multipart file file = { value: '#(pdf)', filename: '../../etc/passwd.pdf', contentType: 'application/pdf' }
    And multipart field invoiceIds = invoiceId
    And multipart field declaredAmount = '80000.00'
    And multipart field currency = 'ARS'
    And multipart field transferDate = today
    And multipart field operationNumber = operationNumber
    And multipart field originAccount = '0170099220000067797370'
    And multipart field holderName = 'Ana Perez'
    When method post
    Then status 201
    And match response.receiptStatus == 'PENDING_REVIEW'
    And match response.paymentStatus == 'PENDING'
    And match response.items == '#[1]'
    And match response.items[0].invoiceId == invoiceId
    * def receiptId = response.receiptId

    Given path '/bank-transfer-receipts'
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    * def mine = karate.filter(response, function(r){ return r.id == receiptId })
    And match mine == '#[1]'
    And match mine[0].invoiceIds == [#(invoiceId)]
    And match mine[0].studentDni == dniAlumno

    Given path '/bank-transfer-receipts', receiptId, 'approve'
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    Given path '/me/payments'
    And header Authorization = 'Bearer ' + alumnoToken
    When method get
    Then status 200
    And match response == '#[1]'

  Scenario: Staff uploads on behalf of the student, rejects, and the student can upload again
    * def operationNumber = 'KAR-ADM-' + java.lang.System.nanoTime()
    Given path '/students', studentId, 'bank-transfer-receipts'
    And header Authorization = 'Bearer ' + adminToken
    And header Idempotency-Key = 'karate-bt-' + operationNumber
    And multipart file file = { value: '#(pdf)', filename: 'c.pdf', contentType: 'application/pdf' }
    And multipart field invoiceIds = invoiceId
    And multipart field declaredAmount = '80000.00'
    And multipart field currency = 'ARS'
    And multipart field transferDate = today
    And multipart field operationNumber = operationNumber
    And multipart field originAccount = 'mi.alias.cbu'
    And multipart field holderName = 'Ana Perez'
    When method post
    Then status 201
    * def receiptId = response.receiptId

    Given path '/bank-transfer-receipts', receiptId, 'reject'
    And header Authorization = 'Bearer ' + adminToken
    And request { rejectionReason: 'No coincide el titular' }
    When method post
    Then status 200
    And match response.status == 'REJECTED'

    * def secondNumber = 'KAR-AGAIN-' + java.lang.System.nanoTime()
    Given path '/me/bank-transfer-receipts'
    And header Authorization = 'Bearer ' + alumnoToken
    And header Idempotency-Key = 'karate-bt-' + secondNumber
    And multipart file file = { value: '#(pdf)', filename: 'c.pdf', contentType: 'application/pdf' }
    And multipart field invoiceIds = invoiceId
    And multipart field declaredAmount = '80000.00'
    And multipart field currency = 'ARS'
    And multipart field transferDate = today
    And multipart field operationNumber = secondNumber
    And multipart field originAccount = 'mi.alias.cbu'
    And multipart field holderName = 'Ana Perez'
    When method post
    Then status 201

  Scenario: Preceptor and the student on the staff endpoint get 403
    Given path '/students', studentId, 'bank-transfer-receipts'
    And header Authorization = 'Bearer ' + preceptorToken
    And header Idempotency-Key = 'karate-bt-forbidden-1'
    And multipart file file = { value: '#(pdf)', filename: 'c.pdf', contentType: 'application/pdf' }
    And multipart field invoiceIds = invoiceId
    And multipart field declaredAmount = '80000.00'
    And multipart field currency = 'ARS'
    And multipart field transferDate = today
    And multipart field operationNumber = 'KAR-403-1'
    And multipart field originAccount = 'alias'
    And multipart field holderName = 'Ana'
    When method post
    Then status 403

    Given path '/students', studentId, 'bank-transfer-receipts'
    And header Authorization = 'Bearer ' + alumnoToken
    And header Idempotency-Key = 'karate-bt-forbidden-2'
    And multipart file file = { value: '#(pdf)', filename: 'c.pdf', contentType: 'application/pdf' }
    And multipart field invoiceIds = invoiceId
    And multipart field declaredAmount = '80000.00'
    And multipart field currency = 'ARS'
    And multipart field transferDate = today
    And multipart field operationNumber = 'KAR-403-2'
    And multipart field originAccount = 'alias'
    And multipart field holderName = 'Ana'
    When method post
    Then status 403

    Given path '/me/bank-transfer-receipts'
    And header Authorization = 'Bearer ' + preceptorToken
    And header Idempotency-Key = 'karate-bt-forbidden-3'
    And multipart file file = { value: '#(pdf)', filename: 'c.pdf', contentType: 'application/pdf' }
    And multipart field invoiceIds = invoiceId
    And multipart field declaredAmount = '80000.00'
    And multipart field currency = 'ARS'
    And multipart field transferDate = today
    And multipart field operationNumber = 'KAR-403-3'
    And multipart field originAccount = 'alias'
    And multipart field holderName = 'Ana'
    When method post
    Then status 403

  Scenario: A file whose content is not what it claims is rejected with 400
    Given path '/me/bank-transfer-receipts'
    And header Authorization = 'Bearer ' + alumnoToken
    And header Idempotency-Key = 'karate-bt-spoofed'
    And multipart file file = { value: 'MZ no soy un pdf', filename: 'falso.pdf', contentType: 'application/pdf' }
    And multipart field invoiceIds = invoiceId
    And multipart field declaredAmount = '80000.00'
    And multipart field currency = 'ARS'
    And multipart field transferDate = today
    And multipart field operationNumber = 'KAR-SPOOF'
    And multipart field originAccount = 'alias'
    And multipart field holderName = 'Ana'
    When method post
    Then status 400
    And match response.violations[0].code == 'BANK_TRANSFER_RECEIPT_FILE_CONTENT_MISMATCH'
