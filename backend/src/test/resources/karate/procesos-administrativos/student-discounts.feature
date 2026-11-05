Feature: Becas y descuentos individuales del alumno

  Background:
    * url baseUrl
    * def fixtures = Java.type('com.red_escuela.backend.karate.KarateFixtures')
    * def administrativoToken = fixtures.tokenForRole('ADMINISTRATIVO')
    * def directorToken = fixtures.tokenForRole('DIRECTOR')
    * def learner = fixtures.createProgramLearnerWithPricing()
    * def thisMonth = '' + java.time.YearMonth.now()
    * def inThreeMonths = '' + java.time.YearMonth.now().plusMonths(3)
    * def lastMonth = '' + java.time.YearMonth.now().minusMonths(1)
    * def nextMonth = '' + java.time.YearMonth.now().plusMonths(1)
    * def proposal = { type: 'SCHOLARSHIP', percent: 50, fromPeriod: '#(nextMonth)', toPeriod: '#(inThreeMonths)', reason: 'Situación económica' }
    * def scholarship = { type: 'SCHOLARSHIP', percent: 50, fromPeriod: '#(thisMonth)', toPeriod: '#(inThreeMonths)', reason: 'Situación económica' }

  Scenario: El director otorga una beca y el alumno la ve en su lectura
    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + directorToken
    And request scholarship
    When method post
    Then status 201
    And match response.status == 'ACTIVE'
    And match response.type == 'SCHOLARSHIP'
    And match response.programEnrollmentId == learner.programEnrollmentId
    * def discountId = response.id

    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + administrativoToken
    When method get
    Then status 200
    And match response[*].id contains discountId
    And match response[0].appliedInstallments == []
    And match response[0].lostPeriods == []

    Given path 'me', 'student-discounts'
    And header Authorization = 'Bearer ' + learner.accessToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].id == discountId
    And match response[0].appliedInstallments == []
    And match response[0].lostPeriods == []

  Scenario: Otro alumno no ve las becas ajenas en su lectura
    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + directorToken
    And request scholarship
    When method post
    Then status 201

    * def other = fixtures.createProgramLearnerWithPricing()
    Given path 'me', 'student-discounts'
    And header Authorization = 'Bearer ' + other.accessToken
    When method get
    Then status 200
    And match response == []

  Scenario: El administrativo propone y el director aprueba la beca
    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + administrativoToken
    And request proposal
    When method post
    Then status 202
    And match response.authorizationRequestId == '#number'
    * def requestId = response.authorizationRequestId

    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response == []

    Given path 'authorization-requests', requestId, 'approve'
    And header Authorization = 'Bearer ' + directorToken
    And request { decisionReason: 'Corresponde' }
    When method post
    Then status 200
    And match response.status == 'APPROVED'

    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + directorToken
    When method get
    Then status 200
    And match response == '#[1]'
    And match response[0].status == 'ACTIVE'

  Scenario: El director revoca la beca con un motivo y sin motivo se rechaza
    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + directorToken
    And request scholarship
    When method post
    Then status 201
    * def discountId = response.id

    Given path 'student-discounts', discountId, 'revocation'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: ' ' }
    When method post
    Then status 400
    And match response.code == 'REVOCATION_REASON_REQUIRED'

    Given path 'student-discounts', discountId, 'revocation'
    And header Authorization = 'Bearer ' + directorToken
    And request { reason: 'Ya no corresponde' }
    When method post
    Then status 201
    And match response.status == 'REVOKED'
    And match response.revokedFromPeriod == '#string'

  Scenario: Una beca que empieza antes de la próxima cuota a emitir es un 409 (D-238)
    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + directorToken
    And request { type: 'SCHOLARSHIP', percent: 50, fromPeriod: '#(lastMonth)', toPeriod: '#(inThreeMonths)', reason: 'Retroactiva' }
    When method post
    Then status 409
    And match response.code == 'SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT'

  Scenario: Dos becas con meses superpuestos sobre la misma inscripción son un 409 (D-401)
    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + directorToken
    And request scholarship
    When method post
    Then status 201

    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + directorToken
    And request scholarship
    When method post
    Then status 409
    And match response.code == 'STUDENT_DISCOUNT_OVERLAP'

  Scenario Outline: Preceptor, docente y alumno no administran descuentos — 403 en cada endpoint
    * def token = '<rol>' == 'ALUMNO' ? learner.accessToken : fixtures.tokenForRole('<rol>')
    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + token
    And request scholarship
    When method post
    Then status 403

    Given path 'course-enrollments', 1, 'student-discounts'
    And header Authorization = 'Bearer ' + token
    And request scholarship
    When method post
    Then status 403

    Given path 'program-enrollments', learner.programEnrollmentId, 'student-discounts'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 403

    Given path 'student-discounts', 1, 'revocation'
    And header Authorization = 'Bearer ' + token
    And request { reason: 'No autorizado' }
    When method post
    Then status 403

    Examples:
      | rol        |
      | PRECEPTOR  |
      | DOCENTE    |
      | ALUMNO     |

  Scenario Outline: Preceptor y docente no leen la vista del alumno — 403
    * def token = fixtures.tokenForRole('<rol>')
    Given path 'me', 'student-discounts'
    And header Authorization = 'Bearer ' + token
    When method get
    Then status 403

    Examples:
      | rol        |
      | PRECEPTOR  |
      | DOCENTE    |
