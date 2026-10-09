Feature: Notas de crédito con aprobación del director

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def administrativoToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')
    * def institutionId = fixtures.createInstitution()
    * def student = fixtures.createConfirmedStudentWithIdentity(institutionId, 'Ana', 'Perez', '' + java.lang.System.nanoTime())
    * def invoiceId = fixtures.createIssuedInvoiceWithLedgerDebit(student.studentId, '80000.00')
    * def noteRequest = { reason: 'BILLING_ERROR', justification: 'Error de carga', lines: [{ lineNumber: 1, amount: 30000.00 }] }

  Scenario: Administrativo pide, director aprueba y el alumno ve la nota de crédito
    Given path 'invoices', invoiceId, 'credit-notes'
    And header Authorization = 'Bearer ' + administrativoToken
    And request noteRequest
    When method post
    Then status 202
    And match response.authorizationRequestId == '#number'
    * def requestId = response.authorizationRequestId

    Given path 'me', 'credit-notes'
    And header Authorization = 'Bearer ' + student.accessToken
    When method get
    Then status 200
    And match response == []

    Given path 'authorization-requests', requestId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    And request { decisionReason: 'Corresponde' }
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    Given path 'me', 'credit-notes'
    And header Authorization = 'Bearer ' + student.accessToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].invoiceId == invoiceId
    And match response[0].creditNoteNumber == '#regex NC-\\d{8}'
    And match response[0].authorizationRequestId == requestId

  Scenario: El director emite directo
    Given path 'invoices', invoiceId, 'credit-notes'
    And header Authorization = 'Bearer ' + directorToken
    And request noteRequest
    When method post
    Then status 201
    And match response.reason == 'BILLING_ERROR'
    And match response.authorizationRequestId == null

  Scenario Outline: Preceptor, alumno y docente no pueden pedir notas de crédito — 403
    * def token = fixtures.tokenForRole('<rol>')
    Given path 'invoices', invoiceId, 'credit-notes'
    And header Authorization = 'Bearer ' + token
    And request noteRequest
    When method post
    Then status 403

    Examples:
      | rol       |
      | PRECEPTOR |
      | ALUMNO    |
      | DOCENTE   |

  Scenario: La nota de crédito manual sobre el alumno ya no existe
    Given path 'students', student.studentId, 'credit-notes'
    And header Authorization = 'Bearer ' + directorToken
    And request noteRequest
    When method post
    Then assert responseStatus == 404 || responseStatus == 405
