Feature: Catálogo de promociones con aprobación del director

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def administrativoToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')
    * def uniqueName = 'Pago anual ' + java.lang.System.nanoTime()
    * def promotion = { name: '#(uniqueName)', discountType: 'ANNUAL_UPFRONT', applicableInvoiceType: 'ANNUAL_UPFRONT', calculation: { type: 'PROPORTIONAL', ratePercent: 15 }, availableFrom: '2030-01-01', priority: 1 }

  Scenario: Administrativo propone, director aprueba y la promoción aparece en el catálogo
    Given path 'discount-definitions'
    And header Authorization = 'Bearer ' + administrativoToken
    And request promotion
    When method post
    Then status 202
    And match response.authorizationRequestId == '#number'
    * def requestId = response.authorizationRequestId

    Given path 'discount-definitions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response[*].name !contains uniqueName

    Given path 'authorization-requests', requestId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    And request { decisionReason: 'Corresponde' }
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    Given path 'discount-definitions'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response[*].name contains uniqueName

  Scenario: El director publica directo y luego cierra la promoción con un motivo
    Given path 'discount-definitions'
    And header Authorization = 'Bearer ' + directorToken
    And request promotion
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    And match response.directPublication == true
    And match response.authorizationRequestId == null
    * def definitionId = response.id

    Given path 'discount-definitions', definitionId, 'closure'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: 'Ya no se ofrece' }
    When method post
    Then status 201
    And match response.status == 'CLOSED'
    And match response.closureReason == 'Ya no se ofrece'

    Given path 'discount-definitions', definitionId, 'closure'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: 'Otra vez' }
    When method post
    Then status 409

  Scenario: Cerrar sin motivo es un 400 con su código
    Given path 'discount-definitions'
    And header Authorization = 'Bearer ' + directorToken
    And request promotion
    When method post
    Then status 201
    * def definitionId = response.id

    Given path 'discount-definitions', definitionId, 'closure'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: ' ' }
    When method post
    Then status 400
    And match response.code == 'DISCOUNT_CLOSURE_REASON_REQUIRED'

  Scenario: La validación en vivo devuelve todas las violaciones y no persiste nada
    * def invalid = { name: ' ', discountType: 'SCHOLARSHIP', applicableInvoiceType: 'ENROLLMENT_FEE', calculation: { type: 'PROPORTIONAL', ratePercent: 10 }, availableFrom: '2030-01-01', priority: 0 }
    Given path 'discount-definitions', 'validation'
    And header Authorization = 'Bearer ' + administrativoToken
    And request invalid
    When method post
    Then status 200
    And match response.violations[*].code contains 'SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS'
    And match response.violations[*].code contains 'INVALID_DISCOUNT_DEFINITION'

  Scenario: Un tipo de ingreso de cohorte no entra al catálogo
    Given path 'discount-definitions'
    And header Authorization = 'Bearer ' + directorToken
    And request { name: 'Ingreso', discountType: 'COHORT_ENTRY', applicableInvoiceType: 'ENROLLMENT_FEE', calculation: { type: 'PROPORTIONAL', ratePercent: 10 }, availableFrom: '2030-01-01', priority: 1 }
    When method post
    Then status 400
    And match response.code == 'DISCOUNT_TYPE_NOT_IN_CATALOG'

  Scenario Outline: Preceptor, docente y alumno no pueden usar el catálogo — 403 en cada endpoint
    * def token = fixtures.tokenForRole('<rol>')
    Given path 'discount-definitions'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 403

    Given path 'discount-definitions'
    And header Authorization = 'Bearer ' + token
    And request promotion
    When method post
    Then status 403

    Given path 'discount-definitions', 'validation'
    And header Authorization = 'Bearer ' + token
    And request promotion
    When method post
    Then status 403

    Given path 'discount-definitions', 1, 'closure'
    And header Authorization = 'Bearer ' + token
    And request { reason: 'No autorizado' }
    When method post
    Then status 403

    Examples:
      | rol       |
      | PRECEPTOR |
      | DOCENTE   |
      | ALUMNO    |
